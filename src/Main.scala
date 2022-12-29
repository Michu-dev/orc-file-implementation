import bloomfilter.mutable.BloomFilter

import java.util.concurrent.TimeUnit
import scala.collection.mutable.ListBuffer
import scala.io.Source

case class TuplesGroup[T](bf: BloomFilter[T], min: T, max: T, contents: T *)
case class Stripe[T](bf: BloomFilter[T], min: T, max: T, tuplesGroups: TuplesGroup[T] *)
case class OrcFile[T](bf: BloomFilter[T], min: T, max: T, stripes: Stripe[T] *)


object Main extends App {

  def showEffectiveness(orcFile: OrcFile[String], word: String): Unit = {
    var startTime = System.nanoTime()
    val contentsByStatistics = orcFile.stripes.flatMap(_.tuplesGroups).map(tuplesGroup => (tuplesGroup.min, tuplesGroup.max)).filter(x => x._1 == word || x._2 == word)
    var endTime = System.nanoTime()
    val elapsedTimeByStatistics = TimeUnit.MILLISECONDS.convert(endTime - startTime, TimeUnit.MILLISECONDS)
    var doesBelong = contentsByStatistics.size > 0

    println(s"Elapsed time by searching ${word} using statistics (ms): ${elapsedTimeByStatistics}")
    println(s"Did used filter find found word using statistics: ${doesBelong}")
    println("------------")
    startTime = System.nanoTime()
    val contentsByBloomFilter = orcFile.stripes.flatMap(_.tuplesGroups).filter(_.bf.mightContain(word))
    endTime = System.nanoTime()
    val elapsedTimeByBloomFilter = TimeUnit.MILLISECONDS.convert(endTime - startTime, TimeUnit.MILLISECONDS)
    doesBelong = contentsByBloomFilter.size > 0

    println(s"Elapsed time by searching ${word} using Bloom filter (ms): ${elapsedTimeByBloomFilter}")
    println(s"Did used filter find found word using Bloom filter: ${doesBelong}")

    println()
  }

  def compareTuplesGroupsNumber(orcFile: OrcFile[String], word: String, isBloomFilter: String): Seq[TuplesGroup[String]] = {
    if (isBloomFilter == "Bloom") {
      return orcFile.stripes.flatMap(_.tuplesGroups).filter(tg => tg.bf.mightContain(word))
    }
    return orcFile.stripes.flatMap(_.tuplesGroups).filter { tg => tg.min == word || tg.max == word }
  }

  val filename = "cano.txt"
  val tuplesGroupNumber = 10000
  val stripeNumber = 100000
  val fileNumber = 1000000
  val falsePositive = 0.001

  // Load the words from the text file
  val words = Source.fromFile("cano.txt").getLines().toList.flatMap(x => x.split(' '))

  // Create a BloomFilter to store the words
  val bf = BloomFilter[String](fileNumber, falsePositive)
  words.foreach(bf.add)

  // Create a list to hold the TuplesGroups
  val tuplesGroups = new ListBuffer[TuplesGroup[String]]()

  // Create TuplesGroups until all the words are added
  var i = 0
  while (i < words.length) {
    val endIndex = Math.min(i + 10000, words.length)
    val tuplesGroupWords = words.slice(i, endIndex)
    val tuplesGroupBf = BloomFilter[String](tuplesGroupNumber, falsePositive)
    tuplesGroupWords.foreach(tuplesGroupBf.add)
    val tuplesGroup = TuplesGroup[String](tuplesGroupBf, tuplesGroupWords.groupBy(identity).minBy(_._2.size)._1, tuplesGroupWords.groupBy(identity).maxBy(_._2.size)._1, tuplesGroupWords: _*)
    tuplesGroups += tuplesGroup
    i = endIndex
  }

  // Create a list to hold the Stripes
  val stripes = new ListBuffer[Stripe[String]]()

  // Create Stripes until all the TuplesGroups are added
  i = 0
  while (i < tuplesGroups.length) {
    val endIndex = Math.min(i + 10, tuplesGroups.length)
    val stripeTuplesGroups = tuplesGroups.slice(i, endIndex)
    val stripeBf = BloomFilter[String](stripeNumber, falsePositive)
    stripeTuplesGroups.foreach(_.contents.foreach(stripeBf.add))
    val stripe = Stripe[String](stripeBf, stripeTuplesGroups.map(_.min).groupBy(identity).minBy(_._2.size)._1, stripeTuplesGroups.map(_.max).groupBy(identity).maxBy(_._2.size)._1, stripeTuplesGroups: _*)
    stripes += stripe
    i = endIndex
  }

  // Create the OrcFile to hold the Stripes
  val orcFile = OrcFile[String](bf, stripes.map(_.min).groupBy(identity).minBy(_._2.size)._1, stripes.map(_.max).groupBy(identity).maxBy(_._2.size)._1, stripes: _*)

  // Compare the effectiveness of using statistics based on min and max with the effectiveness of using Bloom filters
  val mostFrequentWord = words.groupBy(identity).maxBy(_._2.size)._1
  val lessFrequentWord = words.groupBy(identity).minBy(_._2.size)._1
  val searchedWord = "pipe"

  // Compare the number of tuples groups requiring exact analyzing in both cases
  val tuplesGroupsMaxByStatistics = compareTuplesGroupsNumber(orcFile, mostFrequentWord, "Statistics")
  val tuplesGroupsMinByStatistics = compareTuplesGroupsNumber(orcFile, lessFrequentWord, "Statistics")
  val tuplesGroupsPipeByStatistics = compareTuplesGroupsNumber(orcFile, searchedWord, "Statistics")

  val tuplesGroupsMaxByBloomFilter = compareTuplesGroupsNumber(orcFile, mostFrequentWord, "Bloom")
  val tuplesGroupsMinByBloomFilter = compareTuplesGroupsNumber(orcFile, lessFrequentWord, "Bloom")
  val tuplesGroupsPipeByBloomFilter = compareTuplesGroupsNumber(orcFile, searchedWord, "Bloom")

  println(s"Number of tuples groups requiring exact analyzing searching most frequent word (statistics): ${tuplesGroupsMaxByStatistics.size}")
  println(s"Number of tuples groups requiring exact analyzing searching most frequent word (Bloom filter): ${tuplesGroupsMaxByBloomFilter.size}")

  println(s"Number of tuples groups requiring exact analyzing searching less frequent word (statistics): ${tuplesGroupsMinByStatistics.size}")
  println(s"Number of tuples groups requiring exact analyzing searching less frequent word (Bloom filter): ${tuplesGroupsMinByBloomFilter.size}")

  println(s"Number of tuples groups requiring exact analyzing searching 'pipe' (statistics): ${tuplesGroupsPipeByStatistics.size}")
  println(s"Number of tuples groups requiring exact analyzing searching 'pipe' (Bloom filter): ${tuplesGroupsPipeByBloomFilter.size}")
  println("--------------------------------------------------------------------------------------")

  // Compare the time for searching the words: most frequent, less frequent and 'pipe'
  showEffectiveness(orcFile, mostFrequentWord)
  showEffectiveness(orcFile, lessFrequentWord)
  showEffectiveness(orcFile, searchedWord)

  val sizeOfBloomFilter = orcFile.bf.numberOfBits / 16

  val sizeOfWords = orcFile.stripes.flatMap(_.tuplesGroups).flatMap(_.contents).map(x => x.length()).sum

  println(s"Ratio: ${sizeOfBloomFilter.toDouble / sizeOfWords}")

}