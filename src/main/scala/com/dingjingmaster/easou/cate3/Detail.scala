package com.dingjingmaster.easou.cate3

import Params.HDFS
import Params.HDFS_ITEMINFO
import Params.HDFS_CHAPTER
import Params.HDFS_CATE3BASE
import com.huaban.analysis.jieba.JiebaSegmenter
import org.apache.spark.{SparkConf, SparkContext}

import scala.collection.mutable.ArrayBuffer
/**
  * 数据准备
  *   1. 书名切词
  *   2. 作者名切词
  *   3. 第三级分类
  *   4. 去除三级分类中共有的
  *   5.
  */
object Detail {
  def main(args: Array[String]): Unit = {
    val dateStr = "2019-04-15"
    val chapterPath = HDFS + "/rs/dingjing/item_norm/2019-01-28/chapter_info/"
    val conf = new SparkConf()
                .setAppName("category3_detail")
                .set("spark.executor.memory", "10g")
                .set("spark.driver.memory", "4g")
                .set("spark.cores.max", "10")
                .setMaster("spark://qd01-tech2-spark001:7077,qd01-tech2-spark002:7077")
    val sc = new SparkContext(conf)

    /* 书名切词 */
    val gidNameRDD = sc.textFile(HDFS_ITEMINFO)
                .map(x => x.split("\\t"))
                .filter(x => x.length >= 30)
                .map(x => (x(0), x(4), x(30), x(18))) // gid norm_name fee_flag tag1
                .filter(x => ("" != x._1) && ("" != x._2) && ("" != x._4))
                .map(x => (x._1, (cut_name(x._2), x._3, detail_tag13(x._4))))
    /* 章节名切词 */
    val gidChapterRDD = sc.textFile(HDFS_CHAPTER)
                .map(x => x.split("\\t"))
                .filter(x => x.length >= 3)
                .map(x => (x(0), x(2)))
    /* 书名 + 章节名 */
    val itemInfoRDDt = gidNameRDD.join(gidChapterRDD)
    val itemInfoRDD = itemInfoRDDt.map(x => (x._1, x._2._1._2 + "\t" + x._2._1._3 + "\t" + x._2._1._1 + "{]" + x._2._2))

    /* 切词保存 */
    itemInfoRDD.map(x=>x._1 + "\t" + x._2)
                  .repartition(1).saveAsTextFile(HDFS_CATE3BASE + "/word/")

    /* 去除分类中共有的词 */
    val commonWordRDDt = itemInfoRDD.map(x => x._2.split("\t")).map(x => (x(2).split("\\{\\]").toList, x(1)))
                  .flatMap(common_word)
                  .map(x => (x._1, x._2)).reduceByKey(_++_)
                  .saveAsTextFile(HDFS_CATE3BASE + "/common_word/")
  }

  def common_word(x: Tuple2[List[String], String]): Seq[Tuple2[String, Seq[String]]] = {
    val a = ArrayBuffer[Tuple2[String, Seq[String]]]()
    for (i <- x._1.indices) {
      if(x._1(i).length > 1) {
        a.append((x._1(i), Seq(x._2)))
      }
    }
    for(i <- a)
      yield i
  }
  def cut_name(x: String): String = {
    val arr = new ArrayBuffer[String]()
    val js = new JiebaSegmenter()
    val it = js.process(x, JiebaSegmenter.SegMode.SEARCH).listIterator()
    while (it.hasNext){
      arr.append(it.next().word)
    }
    arr.mkString("{]")
  }
  def detail_tag13(x: String): String = {
    var tg = ""
    val arr = x.split("\\,")
    if (arr.length >= 2) {
      tg = arr(1)
    }
    tg
  }
}
