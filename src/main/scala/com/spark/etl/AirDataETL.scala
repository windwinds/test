package com.spark.etl

import java.util

import org.apache.spark.rdd.RDD

import scala.io.Source

class AirDataETL extends Serializable {

  val columns = Array("time", "city", "site", "aqi", "pm2.5", "pm2.5_24h", "pm10", "pm10_24h",
    "so2", "so2_24h", "no2", "no2_24h", "o3", "o3_24h", "o3_8h", "o3_8h_24h", "co",
    "co_24h")
  val cityFile = "data/province_city.txt"
  val locationFile = "data/location.txt"

  /**
    * 判断数组中包含空值的数据
    * @param array
    * @return
    */
  def isContainNull(array: Array[String]):Boolean={

    if (array.length < columns.length)
      return false

    for (value <- array){
      if (value == null || value == "" || value.contains("  "))
        return false
    }
    return true
  }


  /**
    * 去除rdd中不全的数据
    * @param inputRDD
    * @return
    */
  def cleanWrongData(inputRDD: RDD[String]):RDD[Array[String]]={

    inputRDD.map(x => x.split(",")).filter(isContainNull)

  }

  def arrayToString(array: Array[String]):String={

    var result = ""
    for (i <- Range(0, array.length)){
      if (i < array.length-1){
        result += array(i) + ","
      }else{
        result += array(i)
      }
    }
    return result
  }

  /**
    * 得到指定污染物的RDD（"time", "city", "site"一直包含）
    * @param inputRdd
    * @param pollutions
    * @return
    */
  def getRddByPollutionName(inputRdd: RDD[Array[String]], pollutions: Array[String]): RDD[Array[String]]={

    val columnIdArray = new Array[Int](pollutions.length)
    var index = 0
    for (pollution <- pollutions){
      var flag = true
      for (i <- Range(0, columns.length) if flag){
        if (columns(i).equals(pollution)){
          columnIdArray(index) = i
          index += 1
          flag = false
        }
      }
    }
    val resultRdd = inputRdd.map(array => selectArrayByName(array, columnIdArray))
    return resultRdd
  }

  def selectArrayByName(array: Array[String], idArray: Array[Int]): Array[String] ={
    val len = idArray.length
    val result = new Array[String](len)
    for (i <- Range(0, len)){
      result(i) = array(idArray(i))
    }
    return result
  }

  /**
    * 将RDD作为字符串输出
    * @param inputRdd
    * @param num: 输出条数
    */
  def printRdd(inputRdd: RDD[Array[String]], num: Int):Unit={

    inputRdd.map(arrayToString).top(num).foreach(println)

  }

  /**
    * 读取省市文件，得到市为key，省为value的Map
    * @return
    */
  def getCityToProvinceMap():util.HashMap[String, String]={
    val hashMap = new util.HashMap[String, String]()
    val file = Source.fromFile(cityFile)
    for (line <- file.getLines()){
      val str = line.split(" ")
      hashMap.put(str(1), str(0))
    }
    hashMap
  }

  /**
    * 在输入的rdd中加上城市对应省的信息
    * @param inputRdd
    * @param cityMap
    * @return
    */
  def addProvinceForRdd(inputRdd: RDD[Array[String]],
                        cityMap: util.HashMap[String, String]):RDD[Array[String]]={
    inputRdd.map(stringArray => {
      val cityName = stringArray(1)
      val provinceName = cityMap.get(cityName)
      val resultArray = new Array[String](stringArray.length+1)
      resultArray(0) = stringArray(0)
      resultArray(1) = provinceName
      for (i <- Range(1, stringArray.length)){
        resultArray(i+1) = stringArray(i)
      }
      resultArray
    })
  }

  /**
    * 得到rdd中所有地址，不能有重复
    * @param inputRdd
    * @return
    */
  def getAddressArrayFromRdd(inputRdd: RDD[Array[String]]):Array[String]={
    val addressRdd = inputRdd.map(stringArray => stringArray(1) + stringArray(2) + stringArray(3)).distinct()
    val resultArray = addressRdd.collect()
    resultArray
  }


  /**
    * 读取位置文件，得到地址为key，经纬度为value的Map
    * @return
    */
  def getAddressToCoordinateMap():util.HashMap[String, Array[String]]={
    val hashMap = new util.HashMap[String, Array[String]]()
    val file = Source.fromFile(locationFile)
    for (line <- file.getLines()){
      val str = line.split(" ")
      hashMap.put(str(0), Array(str(1), str(2)) )
    }
    hashMap
  }

  /**
    * 在添加省信息后的rdd基础上，加上经纬度坐标信息
    * @param inputRdd
    * @param addressMap
    * @return
    */
  def addLngAndLatForRdd(inputRdd: RDD[Array[String]],
                         addressMap: util.HashMap[String, Array[String]]):RDD[Array[String]]={
    inputRdd.map(stringArray => {
      val address = stringArray(1) + stringArray(2) + stringArray(3)
      val lngLatArray = addressMap.get(address)
      val resultArray = new Array[String](stringArray.length+2)
      for (i <- Range(0, 4)){
        resultArray(i) = stringArray(i)
      }
      resultArray(4) = lngLatArray(0)
      resultArray(5) = lngLatArray(1)
      for (i <- Range(4, stringArray.length)){
        resultArray(i+2) = stringArray(i)
      }
      resultArray
    })
  }

}
