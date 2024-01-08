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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.example.myaudiorecorder.R
import com.example.myaudiorecorder.databinding.ActivityMainBinding
import com.example.myaudiorecorder.db.AudioDataBase
import com.example.myaudiorecorder.db.AudioRecord
import com.example.myaudiorecorder.ui.view.Adapter
import com.example.myaudiorecorder.ui.view.WaveFormView
import com.google.android.material.bottomsheet.BottomSheetBehavior
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
        bottomSheetBehavior.addBottomSheetCallback(object :
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
        // Filtra los elementos que están marcados como isChecked = true
        val selectedRecords = records.filter { it.isChecked }
        if (selectedRecords.isNotEmpty()) {
            val intent = Intent().apply {
                action = Intent.ACTION_SEND_MULTIPLE
                type = "audio/*"

                val uris = ArrayList<Uri>()
                for (record in selectedRecords) {
                    val file = File(record.filepath)
                    val uri = FileProvider.getUriForFile(
                        this@MainActivity,
                        this@MainActivity.packageName + ".fileprovider",
                        file
                    )
                    uris.add(uri)
                }
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
            startActivity(Intent.createChooser(intent, "Compartir grabaciones"))
        } else {
            Toast.makeText(
                this,
                "No se han seleccionado grabaciones para compartir",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun deleteRecords() {
        //builder crea el dialogo para cancelar/confimar la accion
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Borrar audio?")
        //nbRecords cuenta cuantos audios hay con la condicion isChecked=true
        val nbRecords = records.count { it.isChecked }
        builder.setMessage("¿Está seguro de que quiere borrar $nbRecords audio/s?")
        builder.setPositiveButton("Borrar") { _, _ ->
            val toDelete = records.filter { it.isChecked }.toTypedArray()
            //elimina los audio y reconstruye la vista
            GlobalScope.launch {
                this.run {
                    db.audioRecordDao().delete(toDelete)
                    records.removeAll(toDelete)
                    withContext(Dispatchers.Main) {
                        mAdapter.notifyDataSetChanged()
                        leaveEditMode()
                    }
                }
            }
        }
        builder.setNegativeButton("Cancelar") { _, _ ->
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

    private fun playPausePlayer() {
        val root: View = binding.root
        val seekBar = root.findViewById<SeekBar>(R.id.seekBar)
        val ibPlay = root.findViewById<ImageButton>(R.id.ibPlay)
        seekBar.max = mediaPlayer.duration
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.start()
            ibPlay.setImageResource(R.drawable.ic_pause_24)
            handler.postDelayed(runnable, delay)
        } else {
            mediaPlayer.pause()
            ibPlay.setImageResource(R.drawable.ic_play_24)
            handler.removeCallbacks(runnable)
        }
    }

    private fun save() {
        val waveFormView = binding.waveForm
        waveFormView.visibility = View.GONE
        val recyclerRec = binding.recyclerRec
        recyclerRec.visibility = View.VISIBLE
        val root = binding.root
        val recTitle = root.findViewById<TextInputEditText>(R.id.recTitle)

        val newFileName = recTitle.text.toString()

        if (filename != newFileName) {
            val oldFile = File(filename)
            val newFile = File(oldFile.parentFile, "$newFileName.mp3")

            if (oldFile.renameTo(newFile)) {
                filename = newFile.absolutePath
            } else {
                Toast.makeText(
                    this,
                    "No se pudo cambiar el nombre del archivo.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        val filePath = filepath
        val timestamp = Date().time
        val ampsPath = filename
        try {
            var fos = FileOutputStream(ampsPath)
            var out = ObjectOutputStream(fos)
            out.writeObject(amplitudes)
            fos.close()
            out.close()
        } catch (exceptio: IOException) {
            var record = AudioRecord(newFileName, filePath, timestamp, duration, ampsPath)
            GlobalScope.launch {
                db.audioRecordDao().Insert(record)
            }
        }
        recyclerRec.apply {
            adapter = mAdapter
            layoutManager = LinearLayoutManager(context)
            fetchAll()
        }
        sizeUpdateTimer?.stop()
        sizeUpdateTimer = null
        stopRecorder()
    }

    private fun fetchAll() {
        GlobalScope.launch {
            records.clear()
            var queryResult = db.audioRecordDao().getAll()
            records.addAll(queryResult)

            mAdapter.notifyDataSetChanged()
        }
    }

    private fun pauseRecorder() {
        recorder.pause()
        isPaused = true
        timer.pause()

        val ibRec = binding.ibRec
        ibRec.setImageResource(R.drawable.ic_stop_24)
    }

    private fun resumeRecording() {
        recorder.resume()
        isPaused = false
        timer.start()

        val ibRec = binding.ibRec
        ibRec.setImageResource(R.drawable.ic_pause_24)
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
        //limpia waveForm antes de comenzar a grabar
        waveFormView.clearData()
        val recyclerRec = binding.recyclerRec
        recyclerRec.visibility = View.GONE
        val ibCancel = binding.ibCancel
        ibCancel.isClickable = true

        //captura la fecha actual
        val simpleDateFormat = SimpleDateFormat("yyyy.MM.DD_hh.mm", Locale.getDefault())
        val date = simpleDateFormat.format(Date())
        //establece la ruta al archivo
        val externalStorageDir = this.getExternalFilesDir(null)
        filename = "Recorder_$date"

        if (externalStorageDir != null) {

            val recorder = MediaRecorder()
            //setea la calidad del audio seleccionada
            val spinner = binding.spinner
            when (spinner.selectedItem.toString()) {
                "Calidad Alta" -> {
                    recorder.setAudioEncodingBitRate(96000)
                    recorder.setAudioSamplingRate(44100)
                }

                "Calidad Media" -> {
                    recorder.setAudioEncodingBitRate(64000)
                    recorder.setAudioSamplingRate(22050)
                }

                "Calidad Baja" -> {
                    recorder.setAudioEncodingBitRate(32000)
                    recorder.setAudioSamplingRate(11025)
                }
            }
            filepath = "${externalStorageDir?.absolutePath}/$filename"

            //setea mediaRecorder
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(filepath)

                try {
                    prepare()
                    start()

                    timer.start()

                    val handler = Handler()
                    handler.postDelayed(object : Runnable {
                        override fun run() {
                            if (isRecording) {
                                //va mostrando el tamaño del archivo de audio
                                val outputFile = File(filepath)
                                val fileSize = outputFile.length()
                                val fileSizeString = when {
                                    fileSize >= 1024 * 1024 -> String.format(
                                        "%.2f MB",
                                        fileSize.toFloat() / (1024 * 1024)
                                    )

                                    fileSize >= 1024 -> String.format(
                                        "%.2f KB",
                                        fileSize.toFloat() / 1024
                                    )

                                    else -> String.format("%d bytes", fileSize)
                                }
                                val tvSize = binding.tvSize
                                tvSize.text = fileSizeString
                                //el tamaño se actualiza cada medio segundo
                                handler.postDelayed(this, 500)
                            }
                        }
                    }, 0)
                } catch (exception: IOException) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error al iniciar la grabación: $exception",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                isRecording = true
            }

            this.recorder = recorder
        } else {
            Toast.makeText(
                this,
                "No se pudo acceder al almacenamiento externo",
                Toast.LENGTH_SHORT
            ).show()
        }

        val ibRec = binding.ibRec
        ibRec.setImageResource(R.drawable.ic_pause_24)
    }

    private fun stopRecorder() {
        val root = binding.root
        val tvTimer = root.findViewById<TextView>(R.id.tvTimer)
        val ibRec = root.findViewById<ImageButton>(R.id.ibRec)
        val tvSize = root.findViewById<TextView>(R.id.tvSize)
        tvSize.setText("0.0 Mb")
        tvTimer.setText("00:00:00")
        ibRec.setImageResource(R.drawable.ic_stop_24)
        waveFormView.visibility = View.GONE
        recyclerRec.visibility = View.VISIBLE
        if (isRecording) {
            //libera la instancia de mediaRecorder
            timer.stop()
            recorder.stop()
            recorder.reset()
            recorder.release()
            isRecording = false
            isPaused = false
        }
        sizeUpdateTimer?.stop()
        sizeUpdateTimer = null
        val btnRec = binding.ibRec
        btnRec.setImageResource(R.drawable.ic_stop_24)
        waveFormView.reset()
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
            //crea y setea nueva instan
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