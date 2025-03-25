package com.example.hearingmobilityapp

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

object GTFSFileReader {

    fun readGTFSFile(context: Context, fileName: String): List<Map<String, String>> {
        val resultList = mutableListOf<Map<String, String>>()

        try {
            context.assets.open(fileName).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val headers = parseCsvLine(reader.readLine() ?: return emptyList())
                    
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        try {
                            val values = parseCsvLine(line ?: continue)
                            if (values.size == headers.size) {
                                val row = headers.zip(values).toMap()
                                resultList.add(row)
                            } else {
                                Log.w("GTFSFileReader", "Skipping malformed line in $fileName: $line")
                            }
                        } catch (e: Exception) {
                            Log.e("GTFSFileReader", "Error parsing line in $fileName: $line", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GTFSFileReader", "Error reading GTFS file: $fileName", e)
        }

        return resultList
    }

    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        var currentValue = StringBuilder()
        var insideQuotes = false
        
        for (char in line) {
            when {
                char == '"' -> insideQuotes = !insideQuotes
                char == ',' && !insideQuotes -> {
                    values.add(currentValue.toString().trim().removeSurrounding("\""))
                    currentValue = StringBuilder()
                }
                else -> currentValue.append(char)
            }
        }
        values.add(currentValue.toString().trim().removeSurrounding("\""))
        
        return values
    }
}
