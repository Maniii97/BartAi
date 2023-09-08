package com.example.bartai.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.regex.Pattern

class Api {

    private val keys=Keys()
    private val summaryApiUrl = "https://api-inference.huggingface.co/models/facebook/bart-large-cnn"
    private val summaryApiKey = keys.summaryApiKey

    private val youtubeApiUrl = "https://youtube-video-subtitles-list.p.rapidapi.com/"
    private val youtubeApiKey = keys.youtubeApiKey

    private val client = OkHttpClient()

    private var dictionary = mutableMapOf<String, Double>()
    private lateinit var dataList: List<String>

    suspend fun getSummary(input: String): JSONArray {
        val payload = JSONObject()
        payload.put("inputs", input)

        val requestBody = RequestBody.create("application/json".toMediaTypeOrNull(), payload.toString())
        val request = buildRequest(summaryApiUrl, summaryApiKey, requestBody)

        return apiRequest(request)
    }

    suspend fun getYTCaptions(videoId: String): String {
        val request = buildRequest(youtubeApiUrl, youtubeApiKey, null, videoId)
        val response: JSONArray=apiRequest(request)
        var captions=""
        if (response.length() > 0) {
            val firstObject = response.getJSONObject(0)
            val baseUrl = firstObject.getString("baseUrl")
            Log.v("Base url", baseUrl)
            captions=processCaptions(baseUrl)
        }
        else{
            captions="Invalid Video link or No english subtitle found!"
        }
        return captions
    }

    private suspend fun processCaptions(baseUrl: String): String {
        val request = Request.Builder()
            .url(baseUrl)
            .get()
            .build()

        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            try {
                val response = client.newCall(request).execute()
                val responseStr = response.body?.string() ?: ""
                var responseBody = decodeHtmlEntities(responseStr)
                responseBody=responseBody.substring(39)
                val pattern = Pattern.compile("""<text start="([0-9]+(?:\.[0-9]+)?)\" dur="([0-9]+(?:\.[0-9]+)?)\">([^<]+)</text>""")
                val matcher = pattern.matcher(responseBody)

                while (matcher.find()) {
                    val start = matcher.group(1).toDouble()
                    val text = matcher.group(3)
                    dictionary[text as String] = start
                }
                println(dictionary)
                val captions = dictionary.keys.joinToString(" ")
                dataList = dictionary.keys.toList()
                println(dataList)

                Log.v("Final Captions", captions)

                captions
            } catch (e: IOException) {
                Log.e("ApiHelper caption error", "Error fetching captions: ${e.message}")
                ""
            }
        }
    }
    private fun decodeHtmlEntities(input: String): String {
        val htmlEntities = mapOf(
            "&amp;" to "&",
            "&lt;" to "<",
            "&gt;" to ">",
            "&quot;" to "\"",
            "&#39;" to "'"
        )

        var decoded = input
        for ((entity, value) in htmlEntities) {
            decoded = decoded.replace(entity, value)
        }
        return decoded
    }

    suspend fun getVectorEmbeddings(inputQuery: String): List<String> {
        val payload = JSONObject()
        payload.put(
            "inputs",
            JSONObject(mapOf("source_sentence" to inputQuery, "sentences" to dataList))
        )
        val apiUrl = "https://api-inference.huggingface.co/models/sentence-transformers/all-MiniLM-L6-v2"

        val requestBody = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = buildRequest(apiUrl, summaryApiKey, requestBody)

        val output = apiRequest(request)
        println(output)
        val embeddings = mutableListOf<Double>()
        for (i in 0 until output.length()) {
            embeddings.add(output.getDouble(i))
        }
        val topKEmbeddings = getTopKValues(embeddings, 3)

        for ((index, value) in topKEmbeddings) {
            println("Index: $index, Value: $value")
            val text = dataList[index]
            println(text)
            println(dictionary[text])
        }
        val resultList = mutableListOf<String>()

        if(topKEmbeddings.isNotEmpty()) {
            val firstIndex = topKEmbeddings[0].first
            val text = dataList[firstIndex]
            val sectionTime = (dictionary[text]).toString()
            resultList.add(text)
            resultList.add(sectionTime.substringBefore("."))
        }
        else{
            resultList.add("Something went wrong. Please try again!")
            resultList.add("-1s")
        }
        return resultList
    }
    private fun getTopKValues(values: List<Double>, topK: Int): List<Pair<Int, Double>> {

        if (topK >= values.size) {
            return values.mapIndexed { index, value -> Pair(index, value) }
        }

        val indexedValues = values.mapIndexed { index, value -> Pair(index, value) }
        val sortedValues = indexedValues.sortedByDescending { it.second }

        return sortedValues.subList(0, topK)
    }


    private suspend fun apiRequest(request: Request): JSONArray {
        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            try {
                JSONArray(responseBody ?: "[]")
            } catch (e: JSONException) {
                Log.e("ApiHelper", "Error parsing JSON response: ${e.message}")
                JSONArray()
            }
        }
    }
    private fun buildRequest(
        apiUrl: String,
        apiKey: String,
        reqBody: RequestBody?,
        videoId: String? = null
    ): Request {
        val reqBuilder = Request.Builder()
            .url("$apiUrl${videoId?.let { "?videoId=$it&locale=en" } ?: ""}")
            .addHeader("X-RapidAPI-Key", apiKey)
            .addHeader("X-RapidAPI-Host", apiUrl.substringAfter("https://").substringBefore("/"))

        if (reqBody != null) {
            reqBuilder.post(reqBody)
        }
        return reqBuilder.build()
    }

}
