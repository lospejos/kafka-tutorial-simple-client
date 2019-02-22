package com.ippontech.kafkatutorials.simpleclient.plainjson

import com.ippontech.kafkatutorials.simpleclient.Person
import com.ippontech.kafkatutorials.simpleclient.agesTopic
import com.ippontech.kafkatutorials.simpleclient.jsonMapper
import com.ippontech.kafkatutorials.simpleclient.personsTopic
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory

import java.time.Duration
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.util.*

// $ kafka-topics --zookeeper localhost:2181 --create --topic ages --replication-factor 1 --partitions 4

fun main(args: Array<String>) {
    SimpleProcessor("localhost:9092").process()
}

class SimpleProcessor(brokers: String) {

    private val logger = LoggerFactory.getLogger(javaClass) //LogManager.getLogger(javaClass)
    private val consumer = createConsumer(brokers)
    private val producer = createProducer(brokers)

    private fun createConsumer(brokers: String): Consumer<Any, String> {
        val props = Properties()
        props["bootstrap.servers"] = brokers
        props["group.id"] = "person-processor"
        props["key.deserializer"] = ByteArrayDeserializer::class.java //StringDeserializer::class.java
        props["value.deserializer"] = StringDeserializer::class.java
        return KafkaConsumer<Any, String>(props)
    }

    private fun createProducer(brokers: String): Producer<Any, String> {
        val props = Properties()
        props["bootstrap.servers"] = brokers
        props["key.serializer"] = ByteArrayDeserializer::class.java //StringSerializer::class.java
        props["value.serializer"] = StringSerializer::class.java
        return KafkaProducer<Any, String>(props)
    }

    fun process(pollingDurationMs: Long = 300L) {
        consumer.subscribe(listOf(personsTopic))

        logger.info("Consuming and processing data")

        while (true) {
            val records = consumer.poll(Duration.ofMillis(pollingDurationMs) /*Seconds(1)*/)
            logger.info("Received ${records.count()} records")

            records.iterator().forEach {
                val personJson = it.value()
                logger.debug("JSON data: $personJson")

                val person = jsonMapper.readValue(personJson, Person::class.java)
                logger.debug("Person: $person")

                val birthDateLocal = person.birthDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                val age = Period.between(birthDateLocal, LocalDate.now()).getYears()
                logger.debug("Age: $age")

                val key = it.key()
                logger.debug("Will send to key: {}", key)

                val future = producer.send(ProducerRecord(agesTopic, key/*"${person.firstName} ${person.lastName}"*/, "$age"))
                future.get()
            }
        }
    }
}
