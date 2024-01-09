package com.example.myaudiorecorder.control

import android.Manifest
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
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


const val REQUEST_CODE = 200

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity(), Timer.OnTimerTickListener, onItemClickListener {

    private lateinit var binding: ActivityMainBinding
    private var permissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    private var permissionGranted = false
    private var sizeUpdateTimer: Timer? = null
    private lateinit var amplitudes: ArrayList<Float>
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
    private lateinit var vibrator: Vibrator
    private lateinit var waveFormView: WaveFormView
    private lateinit var searchInput: TextInputEditText
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    private lateinit var bottomSheetBehaviorRec: BottomSheetBehavior<LinearLayout>
    private var seekBar: SeekBar? = null
    private var ibPlay: ImageButton? = null

    private lateinit var timer: Timer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissions()

        val root: View = binding.root
        val bottomSheet = root.findViewById<LinearLayout>(R.id.bottomSheet)
        val bottomSheetRec = root.findViewById<LinearLayout>(R.id.bottomSheetRec)
        val ibPlay = root.findViewById<ImageButton>(R.id.ibPlay)
        val seekBar = root.findViewById<SeekBar>(R.id.seekBar)
        val ibBackward = root.findViewById<ImageButton>(R.id.ibBackward)
        val ibForward = root.findViewById<ImageButton>(R.id.ibForward)
        val ibDelete = root.findViewById<ImageButton>(R.id.ibDelete)
        val jumpValue = 1000
        val ibRec = binding.ibRec
        val ibDone = binding.ibDone
        val ibCancel = binding.ibCancel
        val bottomSheetBG = binding.bottomSheetBG
        val btnCancel = root.findViewById<Button>(R.id.btnCancel)
        val recTitle = root.findViewById<TextInputEditText>(R.id.recTitle)
        val btnSave = root.findViewById<Button>(R.id.btnSave)
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

        handler = Handler(Looper.getMainLooper())
        runnable = Runnable {
            if (mediaPlayer.isPlaying) {
                seekBar.max = mediaPlayer.duration
                seekBar.progress = mediaPlayer.currentPosition
                handler.postDelayed(runnable, delay)
            }
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                var query = s.toString()
                searchDatabase(query)
            }

            override fun afterTextChanged(s: Editable?) {
            }
        })

        ibShare.setOnClickListener {
            share()
        }
        ibPlay.setOnClickListener {
            playPausePlayer()
        }
        playPausePlayer()
        ibForward.setOnClickListener {
            mediaPlayer.seekTo(mediaPlayer.currentPosition + jumpValue)
            seekBar.progress += jumpValue
        }
        ibBackward.setOnClickListener {
            mediaPlayer.seekTo(mediaPlayer.currentPosition - jumpValue)
            seekBar.progress -= jumpValue
        }
        ibCancel.setOnClickListener {
            stopRecorder()
            deleteRecording()
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
                recTitle.setText("Recorder_$currentDate.mp3")
            }
        }
        btnCancel.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
        btnSave.setOnClickListener {
            try {
                save()
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                Toast.makeText(this, "Audio guardado", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                Toast.makeText(this, "Error $e", Toast.LENGTH_SHORT).show()
            }
        }
        ibDelete.setOnClickListener {
            deleteRecords()
        }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    //actualiza la posición del MediaPlayer
                    mediaPlayer.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Pausa el MediaPlayer mientras el usuario interactúa con SeekBar
                mediaPlayer.pause()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Reanuda la reproducción cuando el usuario deja de interactuar con SeekBar
                mediaPlayer.start()
            }
        })
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
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.delete_audio_title))

        val nbRecords = records.count { it.isChecked }
        builder.setMessage(getString(R.string.delete_audio_message, nbRecords))

        builder.setPositiveButton(getString(R.string.delete)) { _, _ ->
            val toDelete = records.filter { it.isChecked }.toTypedArray()

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    db.audioRecordDao().delete(toDelete)
                }
                records.removeAll(toDelete)

                withContext(Dispatchers.Main) {
                    mAdapter.notifyDataSetChanged()
                    leaveEditMode()
                }
            }
        }
        builder.setNegativeButton(getString(R.string.cancel)) { _, _ ->
            leaveEditMode()
        }

        builder.show()
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
            val lowercaseQuery = query.toLowerCase()

            // Realizar una búsqueda insensible a mayúsculas y minúsculas
            val queryResult = db.audioRecordDao().searchDatabase("%$lowercaseQuery%")

            withContext(Dispatchers.Main) {
                records.clear()
                records.addAll(queryResult)
                mAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun initViews() {
        val root: View = binding.root
        seekBar = root.findViewById(R.id.seekBar)
        ibPlay = root.findViewById(R.id.ibPlay)
    }

    private fun playPausePlayer() {

        if (seekBar == null || ibPlay == null) {
            initViews()
        }

        seekBar?.max = mediaPlayer.duration

        if (!mediaPlayer.isPlaying) {
            startMediaPlayer()
        } else {
            pauseMediaPlayer()
        }
    }

    private fun startMediaPlayer() {
        mediaPlayer.start()
        ibPlay?.setImageResource(android.R.drawable.ic_media_pause)
        handler.postDelayed(runnable, delay)
    }

    private fun pauseMediaPlayer() {
        mediaPlayer.pause()
        ibPlay?.setImageResource(android.R.drawable.ic_media_play)
        handler.removeCallbacks(runnable)
    }

    private fun save() {
        runOnUiThread {
            binding.waveForm.visibility = View.GONE
            binding.recyclerRec.visibility = View.VISIBLE

            val recTitle = binding.root.findViewById<TextInputEditText>(R.id.recTitle)
            val newFileName = recTitle.text.toString()
            if (filename.equals(newFileName)) {
                val ampsPath = filename
                val record = AudioRecord(newFileName, filepath, Date().time, duration, filename)
                GlobalScope.launch {
                    db.audioRecordDao().Insert(record)
                }
                try {
                    saveAmplitudesToFile(ampsPath)
                } catch (ioException: IOException) {
                    showToast("Error al guardar amplitudes: ${ioException.message}")
                }
                updateRecyclerView()
                stopRecorder()
            } else {
                renameFile(newFileName)
            }
        }
    }

    private fun renameFile(newFileName: String) {
        val oldFile = File(filename)
        val newFile = File(oldFile.parentFile, "$newFileName.mp3")

        if (oldFile.renameTo(newFile)) {
            filename = newFile.absolutePath
        } else {
            showToast("No se pudo cambiar el nombre del archivo.")
        }
    }

    private fun saveAmplitudesToFile(ampsPath: String) {
        FileOutputStream(ampsPath).use { fos ->
            ObjectOutputStream(fos).use { out ->
                out.writeObject(amplitudes)
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun updateRecyclerView() {
        binding.recyclerRec.apply {
            adapter = mAdapter
            layoutManager = LinearLayoutManager(context)
            fetchAll()
        }

        sizeUpdateTimer?.stop()
        sizeUpdateTimer = null
    }

    private fun fetchAll() {
        GlobalScope.launch {
            records.clear()

            try {
                // Cambiar al hilo de fondo para realizar operaciones de base de datos
                val queryResult = withContext(Dispatchers.IO) {
                    db.audioRecordDao().getAll()
                }

                // Cambiar al hilo principal para actualizar la interfaz de usuario
                withContext(Dispatchers.Main) {
                    records.addAll(queryResult)
                    mAdapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                // Manejar cualquier excepción que pueda ocurrir
                Log.e("MainActivity", "Error al obtener datos de la base de datos", e)
            }
        }
    }

    private fun pauseRecorder() {
        recorder.pause()
        timer.pause()

        binding.ibRec?.let {
            it.setImageResource(android.R.drawable.ic_media_pause)
        }
    }

    private fun resumeRecording() {
        recorder.resume()
        timer.start()

        binding.ibRec?.let {
            it.setImageResource(android.R.drawable.ic_media_pause)
        }
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
                    Log.e("MainActivity", "Error al iniciar la grabación", exception)
                    showSnackbar(getString(R.string.recording_error))
                }
                isRecording = true
            }

            this.recorder = recorder
        } else {
            showSnackbar(getString(R.string.storage_access_error))
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

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun stopRecorder() {
        val tvTimer = binding.tvTimer
        val ibRec = binding.ibRec
        val tvSize = binding.tvSize

        tvSize.text = "0.0 Mb"
        tvTimer.text = "00:00:00"
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
        //seleccion de audio para su reproduccion
        val root = binding.root
        var audioRecord = records[position]
        if (mAdapter.isEditMode()) {
            /*si se ejecuto un longClick mAdapter entra en modo edit
            y desabilita la reproduccion*/
            var nbSelected = records.count { it.isChecked }
            if (nbSelected != 0) enableDelete()
            records[position].isChecked = !records[position].isChecked
            mAdapter.notifyItemChanged(position)
        } else {
            //se verifica que no exista ninguna instancia creada
            //de mediaPLayer
            mediaPlayer.stop()
            mediaPlayer.reset()
            mediaPlayer.release()
            //se crea una nueva instancia de media player y se setea
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
        tvRec.setText(audioRecord.ampsPath)
    }

    override fun onItemLongClickListener(position: Int) {
        val root = binding.root
        //edit mode on
        mAdapter.setEditMode(true)
        records[position].isChecked != records[position].isChecked
        mAdapter.notifyItemChanged(position)

        val ibDelete = root.findViewById<ImageButton>(R.id.ibDelete)
        ibDelete.isClickable = true

        enableDelete()
    }
}