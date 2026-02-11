package com.tiviclone.vip

import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import okhttp3.OkHttpClient
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread

data class Channel(
    val name: String, 
    val url: String, 
    val group: String, 
    val logo: String = "",
    val isAdult: Boolean = false
)

class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var rvGroups: RecyclerView
    private lateinit var rvChannels: RecyclerView
    private lateinit var tvChannelName: TextView
    private lateinit var tvStatus: TextView
    private lateinit var uiContainer: View
    private lateinit var etSearch: EditText
    private lateinit var prefs: SharedPreferences

    private val allChannels = mutableListOf<Channel>()
    private val groups = mutableListOf<String>()
    private val channelsInCurrentGroup = mutableListOf<Channel>()
    private var currentGroupSelection = ""
    private var currentPlayingChannel: Channel? = null
    
    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideUI() }

    private var playlistUrl = ""
    private var isVod = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playlistUrl = intent.getStringExtra("URL") ?: "https://raw.githubusercontent.com/Adolfo761/lista-iptv-permanente/main/playlist.m3u"
        isVod = intent.getBooleanExtra("IS_VOD", false)

        prefs = getSharedPreferences("VIP_Prefs", Context.MODE_PRIVATE)
        
        playerView = findViewById(R.id.playerView)
        rvGroups = findViewById(R.id.rvGroups)
        rvChannels = findViewById(R.id.rvChannels)
        tvChannelName = findViewById(R.id.tvChannelName)
        tvStatus = findViewById(R.id.tvStatus)
        uiContainer = findViewById(R.id.uiContainer)
        etSearch = findViewById(R.id.etSearch)

        setupPlayer()

        rvGroups.layoutManager = LinearLayoutManager(this)
        rvChannels.layoutManager = LinearLayoutManager(this)

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filterChannels(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        playerView.setOnClickListener { showUI() }

        loadPlaylist(playlistUrl)
    }

    private fun setupPlayer() {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        val okHttpClient = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()

        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory))
            .build()
        playerView.player = player

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                tvStatus.text = "Error: ${error.message}"
                tvStatus.visibility = View.VISIBLE
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) tvStatus.visibility = View.GONE
                if (state == Player.STATE_BUFFERING) {
                    tvStatus.text = "Cargando..."
                    tvStatus.visibility = View.VISIBLE
                }
            }
        })
    }

    private fun loadPlaylist(urlStr: String) {
        tvStatus.text = "Descargando lista..."
        tvStatus.visibility = View.VISIBLE
        thread {
            try {
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                var line: String?
                var currentName = ""
                var currentGroup = "General"
                var currentLogo = ""
                
                allChannels.clear()
                groups.clear()

                while (reader.readLine().also { line = it } != null) {
                    if (line!!.startsWith("#EXTINF:")) {
                        currentName = line!!.substringAfterLast(",").trim()
                        currentGroup = if (line!!.contains("group-title=\"")) {
                            line!!.substringAfter("group-title=\"").substringBefore("\"")
                        } else "General"
                        currentLogo = if (line!!.contains("tvg-logo=\"")) {
                            line!!.substringAfter("tvg-logo=\"").substringBefore("\"")
                        } else ""
                    } else if (line!!.startsWith("http")) {
                        val isAdult = listOf("ADULTO", "XXX", "+18", "ADULT").any { 
                            currentName.uppercase().contains(it) || currentGroup.uppercase().contains(it) 
                        }
                        allChannels.add(Channel(currentName, line!!.trim(), currentGroup, currentLogo, isAdult))
                        if (!groups.contains(currentGroup)) groups.add(currentGroup)
                    }
                }
                runOnUiThread {
                    tvStatus.visibility = View.GONE
                    setupGroupsAdapter()
                    if (groups.isNotEmpty()) showChannelsForGroup(groups[0])
                }
            } catch (e: Exception) {
                runOnUiThread { 
                    tvStatus.text = "Error al cargar lista"
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupGroupsAdapter() {
        rvGroups.adapter = object : RecyclerView.Adapter<GroupViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = 
                GroupViewHolder(LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false))
            override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
                val g = groups[position]
                holder.tv.text = g
                holder.tv.setTextColor(resources.getColor(R.color.white))
                holder.itemView.setOnClickListener { showChannelsForGroup(g) }
                holder.itemView.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) showChannelsForGroup(g) }
            }
            override fun getItemCount() = groups.size
        }
    }

    private fun showChannelsForGroup(groupName: String) {
        currentGroupSelection = groupName
        channelsInCurrentGroup.clear()
        channelsInCurrentGroup.addAll(allChannels.filter { it.group == groupName })
        setupChannelsAdapter()
    }

    private fun setupChannelsAdapter() {
        rvChannels.adapter = object : RecyclerView.Adapter<ChannelViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = 
                ChannelViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false))
            override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
                val ch = channelsInCurrentGroup[position]
                holder.name.text = ch.name
                Glide.with(this@MainActivity)
                    .load(ch.logo)
                    .placeholder(R.drawable.logo)
                    .error(R.drawable.logo)
                    .into(holder.logo)
                
                holder.itemView.setOnClickListener { checkParentalAndPlay(ch) }
            }
            override fun getItemCount() = channelsInCurrentGroup.size
        }
    }

    private fun checkParentalAndPlay(channel: Channel) {
        if (channel.isAdult) {
            showPinDialog { playChannel(channel) }
        } else {
            playChannel(channel)
        }
    }

    private fun showPinDialog(onSuccess: () -> Unit) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        AlertDialog.Builder(this)
            .setTitle("Control Parental")
            .setMessage("Introduce el PIN (Defecto: 0000)")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                if (input.text.toString() == "0000") onSuccess()
                else Toast.makeText(this, "PIN Incorrecto", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun playChannel(channel: Channel) {
        currentPlayingChannel = channel
        tvChannelName.text = channel.name
        val mediaItem = MediaItem.fromUri(channel.url)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        hideUI()
    }

    private fun filterChannels(query: String) {
        if (query.isEmpty()) {
            showChannelsForGroup(currentGroupSelection)
            return
        }
        channelsInCurrentGroup.clear()
        channelsInCurrentGroup.addAll(allChannels.filter { it.name.lowercase().contains(query.lowercase()) })
        rvChannels.adapter?.notifyDataSetChanged()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        resetHideTimer()
        if (uiContainer.visibility == View.GONE) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MENU -> {
                    showUI()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> { zapChannel(-1); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { zapChannel(1); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun zapChannel(offset: Int) {
        if (channelsInCurrentGroup.isEmpty()) return
        val currentIndex = channelsInCurrentGroup.indexOfFirst { it.url == currentPlayingChannel?.url }
        if (currentIndex != -1) {
            val size = channelsInCurrentGroup.size
            val newIndex = (currentIndex + offset + size) % size 
            checkParentalAndPlay(channelsInCurrentGroup[newIndex])
        }
    }

    private fun showUI() {
        uiContainer.visibility = View.VISIBLE
        resetHideTimer()
    }

    private fun hideUI() {
        if (!etSearch.hasFocus()) uiContainer.visibility = View.GONE
    }
    
    private fun resetHideTimer() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 8000)
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }

    class GroupViewHolder(v: View) : RecyclerView.ViewHolder(v) { val tv = v as TextView }
    class ChannelViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val name = v.findViewById<TextView>(R.id.tvChannelName)
        val logo = v.findViewById<ImageView>(R.id.ivChannelLogo)
    }
}
