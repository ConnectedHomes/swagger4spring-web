package com.wordnik.swagger.converter

import com.wordnik.swagger.core._
import com.wordnik.swagger.model._
import org.slf4j.LoggerFactory

import scala.collection.mutable.{HashMap, HashSet, ListBuffer}

object CustomModelConverters {
  private val LOGGER = LoggerFactory.getLogger(CustomModelConverters.getClass)
  val ComplexTypeMatcher = "([a-zA-Z]*)\\[([a-zA-Z\\.\\-\\$]*)\\].*".r // TODO fix for generics

  val converters = new ListBuffer[ModelConverter]() ++ List(
    new JodaDateTimeConverter,
    new CustomSwaggerSchemaConverter
  )

  def addConverter(c: ModelConverter, first: Boolean = false) = {
    if(first) {
      val reordered = List(c) ++ converters
      converters.clear
      converters ++= reordered
    }
    else converters += c
  }

  def read(cls: Class[_]): Option[Model] = {
    var model: Option[Model] = None
    val itr = converters.iterator
    while(model == None && itr.hasNext) {
      itr.next.read(cls) match {
        case Some(m) => {
          val filteredProperties = m.properties.filter(prop => skippedClasses.contains(prop._2.qualifiedType) == false)
          model = Some(m.copy(properties = filteredProperties))
        }
        case _ => model = None
      }
    }
    model
  }

  def readAll(cls: Class[_]): List[Model] = {
    val output = new HashMap[String, Model]
    val model = read(cls)

    // add subTypes
    model.map(_.subTypes.map(typeRef => {
      try{
        LOGGER.debug("loading subtype " + typeRef)
        val cls = SwaggerContext.loadClass(typeRef)
        read(cls) match {
          case Some(model) => output += cls.getName -> model
          case _ =>
        }
      }
      catch {
        case e: Exception => LOGGER.error("can't load class " + typeRef)
      }
    }))

    // add properties
    model.map(m => {
      output += cls.getName -> m
      val checkedNames = new HashSet[String]
      addRecursive(m, checkedNames, output)
    })
    output.values.toList
  }

  def addRecursive(model: Model, checkedNames: HashSet[String], output: HashMap[String, Model]): Unit = {
    if(!checkedNames.contains(model.name)) {
      val propertyNames = new HashSet[String]
      for((name, property) <- model.properties) {
        val propertyName = property.items match {
          case Some(item) => item.qualifiedType.getOrElse(item.`type`)
          case None => property.qualifiedType
        }
        val name = propertyName match {
          case ComplexTypeMatcher(containerType, basePart) => basePart
          case e: String => e
        }
        propertyNames += name
      }
      for(typeRef <- propertyNames) {
        if(ignoredPackages.contains(getPackage(typeRef))) None
        else if(ignoredClasses.contains(typeRef)) {
          None
        }
        else if(!checkedNames.contains(typeRef)) {
          try{
            checkedNames += typeRef
            val cls = SwaggerContext.loadClass(typeRef)
            LOGGER.debug("reading class " + cls)
            CustomModelConverters.read(cls) match {
              case Some(model) => {
                output += typeRef -> model
                addRecursive(model, checkedNames, output)
              }
              case None =>
            }
          }
          catch {
            case e: ClassNotFoundException =>
          }
        }
      }
    }
  }

  def toName(cls: Class[_]): String = {
    var name: String = null
    val itr = converters.iterator
    while(name == null && itr.hasNext) {
      name = itr.next.toName(cls)
    }
    name
  }

  def getPackage(str: String): String = {
    str.lastIndexOf(".") match {
      case -1 => ""
      case e: Int => str.substring(0, e)
    }
  }

  def ignoredPackages: Set[String] = {
    (for(converter <- converters) yield converter.ignoredPackages).flatten.toSet
  }

  def ignoredClasses: Set[String] = {
    (for(converter <- converters) yield converter.ignoredClasses).flatten.toSet
  }

  def skippedClasses: Set[String] = {
    (for(converter <- converters) yield converter.skippedClasses).flatten.toSet
  }
}
