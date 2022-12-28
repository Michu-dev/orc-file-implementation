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
    val tuplesGroupsByStatistics = orcFile.stripes.flatMap(_.tuplesGroups).map(_.contents).filter(tg =>  tg.min == word || tg.max == word)
    var endTime = System.nanoTime()
    val elapsedTimeByStatistics = TimeUnit.MILLISECONDS.convert(endTime - startTime, TimeUnit.MILLISECONDS)
    var doesBelong = tuplesGroupsByStatistics.contains(word)
    println(s"Elapsed time by searching ${word} using statistics: ${elapsedTimeByStatistics}")
    println(s"Did used filter find searched word using statistics: ${doesBelong}")
    println("------------")
    startTime = System.nanoTime()
    val tuplesGroupsByBloomFilter = orcFile.stripes.flatMap(_.tuplesGroups).filter(_.bf.mightContain(word))
    endTime = System.nanoTime()
    val elapsedTimeByBloomFilter = TimeUnit.MILLISECONDS.convert(endTime - startTime, TimeUnit.MILLISECONDS)
    doesBelong = tuplesGroupsByBloomFilter.contains(word)
    println(s"Elapsed time by searching ${word} using Bloom filter: ${elapsedTimeByBloomFilter}")
    println(s"Did used filter find searched word using Bloom filter: ${doesBelong}")

    println()
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
    val tuplesGroup = TuplesGroup[String](tuplesGroupBf, tuplesGroupWords.min, tuplesGroupWords.max, tuplesGroupWords: _*)
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
    val stripe = Stripe[String](stripeBf, stripeTuplesGroups.map(_.min).min, stripeTuplesGroups.map(_.max).max, stripeTuplesGroups: _*)
    stripes += stripe
    i = endIndex
  }

  // Create the OrcFile to hold the Stripes
  val orcFile = OrcFile[String](bf, words.min, words.max, stripes: _*)

  // Compare the effectiveness of using statistics based on min and max with the effectiveness of using Bloom filters
  val mostFrequentWord = words.groupBy(identity).maxBy(_._2.size)._1
  val lessFrequentWord = words.groupBy(identity).minBy(_._2.size)._1
  val searchedWord = "pipe"

  // Compare the number of tuples groups requiring exact analyzing in both cases
  val tuplesGroupsByStatistics = orcFile.stripes.flatMap(_.tuplesGroups).filter { tg => tg.min == mostFrequentWord || tg.min == lessFrequentWord || tg.min == searchedWord ||
    tg.max == mostFrequentWord || tg.max == lessFrequentWord || tg.max == searchedWord }

  val tuplesGroupsByBloomFilter = orcFile.stripes.flatMap(_.tuplesGroups).filter( tg => tg.bf.mightContain(mostFrequentWord) || tg.bf.mightContain(lessFrequentWord) || tg.bf.mightContain(searchedWord))


  println(s"Number of tuples groups requiring exact analyzing (statistics): ${tuplesGroupsByStatistics.size}")
  println(s"Number of tuples groups requiring exact analyzing (Bloom filter): ${tuplesGroupsByBloomFilter.size}")

  // Compare the time for searching the words: most frequent, less frequent and 'pipe'
  showEffectiveness(orcFile, mostFrequentWord)
  showEffectiveness(orcFile, lessFrequentWord)
  showEffectiveness(orcFile, searchedWord)

  val sizeOfBloomFilter = orcFile.bf.numberOfBits / 16

  val sizeOfWords = orcFile.stripes.flatMap(_.tuplesGroups).flatMap(_.contents).map(x => x.length()).sum

  println(s"Ratio: ${sizeOfBloomFilter.toDouble / sizeOfWords}")

}


//object Main extends App {
//
//  val filename = "cano.txt"
//  val tuplesGroupNumber = 10000
//  val stripeNumber = 100000
//  val fileNumber = 1000000
//  val falsePositive = 0.1
//
//  val lines = Source.fromFile(filename).getLines.toList
//  var wordsForTuplesGroups = List()
//  var wordsForStripes = List()
//  var wordsForOrcFile = List()
//
//  var tuplesGroups = List()
//  var stripes = List()
//
//  var wordsCounter = 0
//  var tuplesGroupsCounter = 0
//  var stripesCounter = 0
//
//  var freqMap = wordsForTuplesGroups.groupBy(identity).mapValues(_.size)
//  var max = "dsa"
//  var min = "das"
//  var bf = BloomFilter[String](tuplesGroupNumber, falsePositive)
//
//  for(line <- lines) {
//    val wordsInLine = line.split(" ")
//
//
//    for (word <- wordsInLine) {
//      if (wordsCounter == 10000) {
//        freqMap = wordsForTuplesGroups.groupBy(identity).mapValues(_.size)
//        max = freqMap.maxBy(_._2)._1
//        min = freqMap.minBy(_._2)._1
//        bf = BloomFilter[String](tuplesGroupNumber, falsePositive)
//        tuplesGroups :+ TuplesGroup[String](bf, min, max, max)
//        tuplesGroupsCounter += 1
//
//        if (tuplesGroupsCounter == 10) {
//          freqMap = wordsForStripes.groupBy(identity).mapValues(_.size)
//          max = freqMap.maxBy(_._2)._1
//          min = freqMap.minBy(_._2)._1
//          bf = BloomFilter[String](stripeNumber, falsePositive)
//          stripes :+ Stripe(bf, min, max, tuplesGroups)
//          wordsForStripes = List(word)
//        } else {
//          wordsForStripes :+ word
//        }
//
//        wordsForTuplesGroups = List(word)
//        wordsCounter = 1
//      } else {
//        wordsForTuplesGroups :+ word
//        wordsForStripes :+ word
//        wordsForOrcFile :+ word
//        wordsCounter += 1
//      }
//    }
//  }
//  freqMap = wordsForOrcFile.groupBy(identity).mapValues(_.size)
//  max = freqMap.maxBy(_._2)._1
//  min = freqMap.minBy(_._2)._1
//  bf = BloomFilter[String](fileNumber, falsePositive)
//
//  var orcFile = OrcFile(bf, min, max, stripes)
//
//  println(max)
//
//
//
//
//
//}