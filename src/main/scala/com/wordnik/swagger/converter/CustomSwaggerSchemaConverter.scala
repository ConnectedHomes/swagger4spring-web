package com.wordnik.swagger.converter

import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import com.wordnik.swagger.annotations.ApiModel
import com.wordnik.swagger.model._

import scala.collection.mutable.LinkedHashMap

class CustomSwaggerSchemaConverter
  extends ModelConverter 
  with BaseConverter {

  def read(cls: Class[_]): Option[Model] = {
    Option(cls).flatMap({
      cls => {
        implicit val properties = new LinkedHashMap[String, ModelProperty]()
        new CustomModelPropertyParser(cls).parse

        val p = (for((key, value) <- properties) 
          yield (value.position, key, value)
        ).toList

        val sortedProperties = new LinkedHashMap[String, ModelProperty]()
        p.sortWith(_._1 < _._1).foreach(e => sortedProperties += e._2 -> e._3)

        val parent = Option(cls.getAnnotation(classOf[ApiModel])) match {
          case Some(e) => Some(e.parent.getName)
          case _ => None
        }
        val discriminator = {
          val v = {
            val apiAnno = cls.getAnnotation(classOf[ApiModel])
            if(apiAnno != null && apiAnno.discriminator != null)
              apiAnno.discriminator
            else if(cls.getAnnotation(classOf[JsonTypeInfo]) != null)
              cls.getAnnotation(classOf[JsonTypeInfo]).property
            else ""
          }
          if(v != null && v != "") Some(v)
          else None
        }
        val subTypes = {
          if(cls.getAnnotation(classOf[ApiModel]) != null)
            cls.getAnnotation(classOf[ApiModel]).subTypes.map(_.getName).toList
          else if(cls.getAnnotation(classOf[JsonSubTypes]) != null)
            (for(subType <- cls.getAnnotation(classOf[JsonSubTypes]).value) yield (subType.value.getName)).toList
          else List()
        }
        sortedProperties.size match {
          case 0 => None
          case _ => Some(Model(
            toName(cls),
            toName(cls),
            cls.getName,
            sortedProperties,
            toDescriptionOpt(cls),
            parent,
            discriminator,
            subTypes
          ))
        }
      }
    })
  }
}