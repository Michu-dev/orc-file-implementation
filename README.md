## ORC-file implementation using Bloom Filter

### Overview
The content of the project is right below:
Write a program which:

>case class TuplesGroup[T](bf: BloomFilter[T], min: T, max: T, contents: T *)<br />case class Stripe[T](bf: BloomFilter[T], min: T, max: T, tuplesGroups: TuplesGroup[T] *)<br />
case class OrcFile[T](bf: BloomFilter[T], min: T, max: T, stripes: Stripe[T] *)
- loads words existing in cano.txt file into above classes
- compares effectiveness of using statistics based on values min and max with the effectiveness of using Bloom filters during searching for words:
  - the most frequent word existing in cano.txt
  - one of the less frequent words existing in cano.txt
  - word 'pipe'
- counts relation between total multiplicity Bloom filters and total size of words stored in ORCFile.

Hints:
- Pounce on implementation of Bloom filter available on https://github.com/alexandrnikitin/bloom-filter-scala. It is also available in Maven repositories
- Loading data to above structures take below into account:
  - at first load data to single group of tuples located in a single stripe in a single file
  - only if the group will fill up (10000 rows) add another groups
  - stripe can't contain more than 10 tuples groups, only if the stripe will fill up add another one
  - all data (all stripes) have to be in one file
  - only after load all words count statistics and Bloom filters on all levels
  - Bloom filters have to allow errors false-positive at level 0.1%
  - during creating of Bloom filters always take into account the amount of data what they cover:
    - TuplesGroup - 10000
    - Stripe - 100000
    - OrcFile - 1000000

- During comparing of effectiveness:
  - compare the number of tuples groups requiring exact analysing in both cases (statistics and Bloom filter)
  - compare time needed for finding searched words in both cases (statistics and Bloom filter)

### How to run the project
You need to load the project structure into Intellij structure and just run it. All the needed 
configuration is done beside obvious things like setting up:
- Java 1.8 or higher
- Scala SDK 2.12.14 or higher
- Bloom Filter from Maven repository