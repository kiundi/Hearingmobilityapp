package com.example.hearingmobilityapp

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

object GTFSFileReader {

    fun readGTFSFile(context: Context, fileName: String): List<Map<String, String>> {
        val resultList = mutableListOf<Map<String, String>>()

        try {
            val inputStream = context.assets.open(fileName)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val headers = reader.readLine()?.split(",") ?: return emptyList()

            reader.forEachLine { line ->
                val values = line.split(",")
                if (values.size == headers.size) {
                    val row = headers.zip(values).toMap()
                    resultList.add(row)
                }
            }

            reader.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return resultList
    }
}
