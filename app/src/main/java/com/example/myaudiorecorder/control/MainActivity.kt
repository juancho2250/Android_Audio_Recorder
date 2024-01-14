package com.example.myaudiorecorder.control

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.example.myaudiorecorder.R
import com.example.myaudiorecorder.databinding.ActivityMainBinding
import com.example.myaudiorecorder.db.AudioDataBase
import com.example.myaudiorecorder.db.AudioRecord
import com.example.myaudiorecorder.view.Adapter
import com.example.myaudiorecorder.view.WaveFormView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.jmdev.myaudiorecorder.data.Timer
import com.jmdev.myaudiorecorder.data.onItemClickListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


const val REQUEST_CODE = 200

@OptIn(DelicateCoroutinesApi::class)
@Suppress("DEPRECATION")
@SuppressLint("NotifyDataSetChanged")
class MainActivity : AppCompatActivity(), Timer.OnTimerTickListener, onItemClickListener {

    private lateinit var binding: ActivityMainBinding
    private var permissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    private var permissionGranted = false
    private var sizeUpdateTimer: Timer? = null
    private lateinit var records: MutableList<AudioRecord>
    private lateinit var mAdapter: Adapter
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var recyclerRec: RecyclerView
    private lateinit var db: AudioDataBase
    private lateinit var runnable: Runnable
    private lateinit var handler: Handler
    private var delay = 1000L
    private lateinit var recorder: MediaRecorder
    private var filename = ""
    private lateinit var filepath: String
    private var isRecording = false
    private var isPaused = false
    private var duration = ""
    private var visibility = true
    private lateinit var vibrator: Vibrator
    private lateinit var waveFormView: WaveFormView
    private lateinit var searchInput: TextInputEditText
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    private lateinit var bottomSheetBehaviorRec: BottomSheetBehavior<LinearLayout>
    private var seekBar: SeekBar? = null
    private var ibPlay: ImageButton? = null
    private lateinit var timer: Timer

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissions()

        val root: View = binding.root
        val bottomSheet = root.findViewById<LinearLayout>(R.id.bottomSheet)
        val bottomSheetRec = root.findViewById<LinearLayout>(R.id.bottomSheetRec)
        val ibPlay = root.findViewById<ImageButton>(R.id.ibPlay)
        val buttons = binding.buttons
        val ibDelete = root.findViewById<ImageButton>(R.id.ibDelete)
        val ibRec = binding.ibRec
        val ibDone = binding.ibDone
        val fab = binding.fab
        val ibCancel = binding.ibCancel
        val bottomSheetBG = binding.bottomSheetBG
        val btnCancel = root.findViewById<Button>(R.id.btnCancel)
        val recTitle = root.findViewById<TextInputEditText>(R.id.recTitle)
        val btnSave = root.findViewById<Button>(R.id.btnSave)
        val btnForward = root.findViewById<ImageButton>(R.id.ibForward)
        val spinner = binding.spinner
        val tvTimer = binding.tvTimer
        val options = arrayOf("Calidad Alta", "Calidad Media", "Calidad Baja")
        val adapt = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item, options
        )
        val ibShare = root.findViewById<ImageButton>(R.id.ibShare)

        searchInput = binding.searchInput
        waveFormView = binding.waveForm

        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.peekHeight = 1
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        bottomSheetBehaviorRec = BottomSheetBehavior.from(bottomSheetRec)
        bottomSheetBehaviorRec.peekHeight = 1
        bottomSheetBehaviorRec.state = BottomSheetBehavior.STATE_COLLAPSED

        mediaPlayer = MediaPlayer()
        adapt.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )
        spinner.adapter = adapt

        records = ArrayList()

        db = Room.databaseBuilder(
            this,
            AudioDataBase::class.java, "audioRecords"
        )
            .build()

        mAdapter = Adapter(records, this)

        recyclerRec = binding.recyclerRec
        recyclerRec.apply {
            adapter = mAdapter
            layoutManager = LinearLayoutManager(context)
            fetchAll()
        }
        //state change when manually sliding
        bottomSheetBehavior.addBottomSheetCallback(/* callback = */ object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    bottomSheetBG.visibility = View.GONE
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
            }
        })

        timer = Timer(this)
        vibrator = this.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                searchDatabase(query)
            }

            override fun afterTextChanged(s: Editable?) {
            }
        })

        if(visibility){
            buttons.visibility = View.GONE
            fab.visibility = View.VISIBLE
        } else {
            buttons.visibility = View.VISIBLE
            fab.visibility = View.GONE
        }

        btnForward.setOnClickListener {
            mediaPlayer.stop()
            bottomSheetBehaviorRec.state = BottomSheetBehavior.STATE_COLLAPSED
            fab.visibility = View.VISIBLE
            buttons.visibility = View.GONE
        }
        fab.setOnClickListener {
            fab.visibility = View.GONE
            buttons.visibility = View.VISIBLE
        }
        ibShare.setOnClickListener {
            share()
        }
        ibPlay.setOnClickListener {
            playPausePlayer()
        }
        ibCancel.setOnClickListener {
            stopRecorder()
            deleteRecording()
            fab.visibility = View.VISIBLE
            buttons.visibility = View.GONE
        }
        ibRec.setOnClickListener {
            ibCancel.isClickable = true
            when {
                isPaused -> resumeRecording()
                isRecording -> pauseRecorder()
                else -> startRecording()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        50,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else vibrator.vibrate(50)
        }
        ibDone.setOnClickListener {
            if (isRecording) {
                duration = tvTimer.text.toString()
                stopRecorder()
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                bottomSheetBG.visibility = View.VISIBLE
                val currentDate =
                    SimpleDateFormat("yyyy.MM.dd_hh:mm", Locale.getDefault()).format(Date())
                recTitle.setText("Recorder_$currentDate")
            }
        }
        btnCancel.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
        btnSave.setOnClickListener {
            save()
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            fab.visibility = View.VISIBLE
            buttons.visibility = View.GONE
        }
        ibDelete.setOnClickListener {
            deleteRecords()
        }

    }
    fun hideKeyboard() {
        val root = binding.root
        val recTitle = root.findViewById<TextInputEditText>(R.id.recTitle)
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(recTitle.getWindowToken(), 0)
    }
    private fun checkPermissions() {
        permissionGranted = ActivityCompat.checkSelfPermission(
            this,
            permissions[0]
        ) == PackageManager.PERMISSION_GRANTED

        if (!permissionGranted) {
            requestPermissions()
        }
    }
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE)
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE) {
            permissionGranted =
                grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        }
    }
    private fun share() {
        val selectedRecords = records.filter { it.isChecked }
        if (selectedRecords.isEmpty()) {
            Snackbar.make(
                findViewById(android.R.id.content),
                "No se han seleccionado grabaciones para compartir",
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        val intent = Intent().apply {
            action = Intent.ACTION_SEND_MULTIPLE
            type = "audio/*"

            val uris = ArrayList<Uri>()
            selectedRecords.forEach {
                val file = File(it.filepath)
                val uri =
                    FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
                uris.add(uri)
            }
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }

        startActivity(Intent.createChooser(intent, "Compartir grabaciones"))
    }
    private fun deleteRecords() {
        visibility = true
        hideKeyboard()

        val nbRecords = records.count { it.isChecked }

        if (nbRecords > 0) {
            val builder = AlertDialog.Builder(this)
            builder.setTitle(getString(R.string.delete_audio_title))

            builder.setMessage(getString(R.string.delete_audio_message, nbRecords))

            builder.setPositiveButton(getString(R.string.delete)) { _, _ ->
                val toDelete = records.filter { it.isChecked }.toTypedArray()

                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        db.audioRecordDao().delete(toDelete)
                    }
                    records.removeAll(toDelete.toSet())

                    withContext(Dispatchers.Main) {
                        mAdapter.notifyDataSetChanged()
                        leaveEditMode()
                    }
                }
                showSnackb("Borrado exitoso")
            }
            builder.setNegativeButton(getString(R.string.cancel)) { _, _ ->
                leaveEditMode()
            }
            builder.show()
        } else {
            showSnackb("Mantenga presionado el audio a borrar")
        }
    }
    private fun leaveEditMode() {
        records.map { it.isChecked = false }
        mAdapter.setEditMode(false)
        bottomSheetBehaviorRec.state = BottomSheetBehavior.STATE_COLLAPSED
    }
    private fun enableDelete() {
        val root = binding.root
        val ibDelete = root.findViewById<ImageButton>(R.id.ibDelete)
        ibDelete.isClickable = true
    }
    private fun searchDatabase(query: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val lowercaseQuery = query.toLowerCase(Locale.ROOT)

            // Realizar una búsqueda insensible a mayúsculas y minúsculas
            val queryResult = db.audioRecordDao().searchDatabase("%$lowercaseQuery%")

            withContext(Dispatchers.Main) {
                records.clear()
                records.addAll(queryResult)
                mAdapter.notifyDataSetChanged()
            }
        }
    }
    private fun playPausePlayer() {
        val root = binding.root
        seekBar = root.findViewById(R.id.seekBar)
        ibPlay = root.findViewById(R.id.ibPlay)

        seekBar?.max = mediaPlayer.duration

        // Initialize handler and runnable only if not initialized yet
        if (!::handler.isInitialized) {
            handler = Handler(Looper.getMainLooper())
            runnable = Runnable {
                seekBar?.max = mediaPlayer.duration
                seekBar?.progress = mediaPlayer.currentPosition
                handler.postDelayed(runnable, delay)
            }
        }
        seekBar!!.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                mediaPlayer.pause()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                mediaPlayer.start()
            }
        })

        if (mediaPlayer.isPlaying) {
            // If MediaPlayer is currently playing, pause it
            ibPlay!!.setImageResource(R.drawable.ic_pause_24)
            mediaPlayer.pause()
            handler.removeCallbacks(runnable)

        } else {
            mediaPlayer
            // If MediaPlayer is paused, start it
            ibPlay!!.setImageResource(R.drawable.ic_play_24)
        }
    }
    private fun save() {
        try {
            runOnUiThread {
                binding.waveForm.visibility = View.GONE
                binding.recyclerRec.visibility = View.VISIBLE

                val recTitle = binding.root.findViewById<TextInputEditText>(R.id.recTitle)
                val inputFileName = recTitle?.text?.toString() ?: "default_filename"
                val fileName = "$inputFileName.mp3"
                val record = AudioRecord(fileName, filepath, Date().time, duration, filename)

                GlobalScope.launch {
                    db.audioRecordDao().Insert(record)
                    fetchAll()
                    showSnackb(resources.getString(R.string.saveOk))
                }
                hideKeyboard()
                stopRecorder()
            }
        } catch (e: Exception) {
            showSnackb(resources.getString(R.string.saveNotOk))
        }
    }
    private fun fetchAll() {
        val error = resources.getString(R.string.error)
        GlobalScope.launch {
            records.clear()

            try {
                val queryResult = withContext(Dispatchers.IO) {
                    db.audioRecordDao().getAll()
                }
                withContext(Dispatchers.Main) {
                    records.addAll(queryResult)
                    mAdapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                showSnackb("$error $e")
            }
        }
    }
    private fun pauseRecorder() {
        recorder.pause()
        timer.pause()

        binding.ibRec.setImageResource(android.R.drawable.ic_media_pause)
    }
    private fun resumeRecording() {
        recorder.resume()
        timer.start()

        binding.ibRec.setImageResource(android.R.drawable.ic_media_pause)
    }
    private fun deleteRecording() {
        //detiene la grabacion y elimina el archivo temporal
        if (isRecording) {
            stopRecorder()
            val file = File(filepath)
            if (file.exists()) {
                file.delete()
            }
        }
        bottomSheetBehaviorRec.state = BottomSheetBehavior.STATE_COLLAPSED
    }
    private fun startRecording() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
        }
        val waveFormView = binding.waveForm
        waveFormView.visibility = View.VISIBLE
        waveFormView.clearData()

        val recyclerRec = binding.recyclerRec
        recyclerRec.visibility = View.GONE
        binding.ibCancel.isClickable = true

        val date = SimpleDateFormat("yyyy.MM.DD_hh.mm", Locale.getDefault()).format(Date())
        filename = "Recorder_$date"

        val externalStorageDir = this.getExternalFilesDir(null)

        if (externalStorageDir != null) {
            val recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

                val spinner = binding.spinner
                when (spinner.selectedItem.toString()) {
                    "Calidad Alta" -> {
                        setAudioEncodingBitRate(96000)
                        setAudioSamplingRate(44100)
                    }

                    "Calidad Media" -> {
                        setAudioEncodingBitRate(64000)
                        setAudioSamplingRate(22050)
                    }

                    "Calidad Baja" -> {
                        setAudioEncodingBitRate(32000)
                        setAudioSamplingRate(11025)
                    }
                }

                filepath = "${externalStorageDir.absolutePath}/$filename"
                setOutputFile(filepath)

                try {
                    prepare()
                    start()

                    timer.start()

                    val handler = Handler()
                    handler.postDelayed(object : Runnable {
                        override fun run() {
                            if (isRecording) {
                                updateFileSize()
                                handler.postDelayed(this, 500)
                            }
                        }
                    }, 0)
                } catch (exception: IOException) {
                    showSnackb(getString(R.string.recording_error))
                }
                isRecording = true
            }

            this.recorder = recorder
        } else {
            showSnackb(getString(R.string.storage_access_error))
        }

        binding.ibRec.setImageResource(R.drawable.ic_pause_24)
    }
    private fun updateFileSize() {
        val outputFile = File(filepath)
        val fileSize = outputFile.length()
        val fileSizeString = when {
            fileSize >= 1024 * 1024 -> String.format("%.2f MB", fileSize.toFloat() / (1024 * 1024))
            fileSize >= 1024 -> String.format("%.2f KB", fileSize.toFloat() / 1024)
            else -> String.format("%d bytes", fileSize)
        }
        binding.tvSize.text = fileSizeString
    }
    private fun showSnackb(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
    private fun stopRecorder() {
        val tvTimer = binding.tvTimer
        val ibRec = binding.ibRec
        val tvSize = binding.tvSize

        tvSize.text = resources.getString(R.string.size)
        tvTimer.text = resources.getString(R.string.crono)
        ibRec.setImageResource(R.drawable.ic_stop_24)

        binding.waveForm.visibility = View.GONE
        binding.recyclerRec.visibility = View.VISIBLE

        if (isRecording) {
            // Libera la instancia de MediaRecorder
            timer.stop()
            recorder.stop()
            recorder.release()
            isRecording = false
            isPaused = false
        }

        sizeUpdateTimer?.stop()
        sizeUpdateTimer = null

        binding.waveForm.reset()
    }
    override fun onTimeTick(duration: String) {
        //se envian datos al waveFormView para convertirlos en picos
        val tvTimer = binding.tvTimer
        tvTimer.text = duration
        waveFormView = binding.waveForm
        waveFormView.addAmplitud(recorder.maxAmplitude.toFloat())
    }
    override fun onItemClickListener(position: Int) {
        //select audio to play
        val root = binding.root
        val audioRecord = records[position]
        if (mAdapter.isEditMode()) {
            val nbSelected = records.count { it.isChecked }
            if (nbSelected != 0) enableDelete()
            records[position].isChecked = !records[position].isChecked
            mAdapter.notifyItemChanged(position)
        } else {
            //kill media old player
            mediaPlayer.stop()
            mediaPlayer.reset()
            mediaPlayer.release()
            //new media player
            mediaPlayer = MediaPlayer()
            mediaPlayer.apply {
                setDataSource(audioRecord.filepath)
                prepare()
                start()
                val ibAdapter = root.findViewById<ImageButton>(R.id.ibAdapter)
                ibAdapter.isSelected = true
            }
        }
        val seekBar = root.findViewById<SeekBar>(R.id.seekBar)
        runnable = Runnable {
            seekBar.progress = mediaPlayer.currentPosition
            handler.postDelayed(runnable, delay)
        }
        bottomSheetBehaviorRec.state = BottomSheetBehavior.STATE_EXPANDED
        val tvRec = root.findViewById<TextView>(R.id.tvRec)
        tvRec.text = audioRecord.filename
    }
    override fun onItemLongClickListener(position: Int) {
        val root = binding.root
        visibility = false
        bottomSheetBehaviorRec.state = BottomSheetBehavior.STATE_EXPANDED
        //edit mode on
        mAdapter.setEditMode(true)
        records[position].isChecked = !records[position].isChecked
        mAdapter.notifyItemChanged(position)

        val ibDelete = root.findViewById<ImageButton>(R.id.ibDelete)
        ibDelete.isClickable = true

        enableDelete()
    }
}