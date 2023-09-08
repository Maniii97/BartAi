package com.example.bartai

import ChatAdapter
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bartai.api.Api
import com.example.bartai.databinding.ActivityChatBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val chatMessages = mutableListOf<MessageModel>()
    private lateinit var chatAdapter: ChatAdapter
    private val api = Api()
    private lateinit var progressBar : ProgressBar
    private lateinit var videoId:String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRV()
        videoId = intent.getStringExtra("videoId").toString()
        progressBar = findViewById(R.id.progressBar)
        getSummary(videoId)
    }
    private fun setupRV() {
        chatAdapter = ChatAdapter(chatMessages)
        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity)
            adapter = chatAdapter
        }
    }

    fun onSendButtonClick(view: View) {
        val userInput = binding.etMessage.text.toString()
        if (userInput.isNotEmpty()) {
            userMessage(userInput)
            getAnswer(userInput)
            binding.etMessage.text.clear()
        }
    }

    private fun userMessage(message: String) {
        chatMessages.add(MessageModel(message, true))
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        binding.chatRecyclerView.scrollToPosition(chatMessages.size - 1)
    }

    private fun getAnswer(question: String) {
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.Main).launch {
            val response = api.getVectorEmbeddings(question)
            aiResponse(response[0],response[1])
            progressBar.visibility = View.GONE
        }
    }

    private fun getSummary(videoId: String?) {
        CoroutineScope(Dispatchers.Main).launch {
            val captions: String = api.getYTCaptions(videoId!!)
            val response: JSONArray = api.getSummary(captions)
            if (captions.isNotEmpty()) {
                if (response.length() > 0) {
                    val resultArray = response.getJSONObject(0)
                    var summary = "The summary of the video is: \n"
                    summary += resultArray.getString("summary_text")
                    Log.v("result text", resultArray.toString())
                    aiResponse(summary)
                    aiResponse("Feel free to ask Questions related to this video")
                } else {
                    aiResponse("Hey there, \nI am Bart AI, Feel free to ask Questions related to this video ")
                }
            } else {
                Log.e("captions","No captions in the video link")
            }
        }
    }

    private fun aiResponse(message: String,sectionTiming:String="-1s") {
        progressBar.visibility = View.VISIBLE
        if(sectionTiming=="-1s")
            chatMessages.add(MessageModel(message, false,))
        else{
            val sectionUrl="https://www.youtube.com/watch?v=$videoId&t=$sectionTiming"
            println(sectionUrl)
            chatMessages.add(MessageModel(message, false,sectionUrl))
        }
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        binding.chatRecyclerView.scrollToPosition(chatMessages.size - 1)
        progressBar.visibility = View.GONE
    }

}