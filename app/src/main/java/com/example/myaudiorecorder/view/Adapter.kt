package com.example.myaudiorecorder.view

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myaudiorecorder.R
import com.example.myaudiorecorder.db.AudioRecord
import com.jmdev.myaudiorecorder.data.onItemClickListener
import java.text.SimpleDateFormat
import java.util.Date

class Adapter(var records: MutableList<AudioRecord>, var listener: onItemClickListener) : RecyclerView.Adapter<Adapter.ViewHolder>() {

    private var editMode = false

    fun isEditMode(): Boolean {return editMode}

    fun setEditMode(mode: Boolean){
        if (editMode != mode){
            editMode = mode
            notifyDataSetChanged()
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        View.OnClickListener, View.OnLongClickListener {
        var tvFileName: TextView = itemView.findViewById(R.id.tvFilename)
        var tvMeta: TextView = itemView.findViewById(R.id.tvMeta)
        var checkBox: CheckBox = itemView.findViewById(R.id.checkBox)

        init {
            itemView.setOnClickListener(this)
            itemView.setOnLongClickListener(this)
        }

        override fun onClick(v: View?) {
            val position = adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onItemClickListener(position)
            }
        }

        override fun onLongClick(v: View?): Boolean {
            val position = adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onItemLongClickListener(position)
            }
            return true
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.record_adapter, parent, false)
        return ViewHolder(itemView) // Return the ViewHolder object
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (position != RecyclerView.NO_POSITION) {
            val record = records[position]

            val dsf = SimpleDateFormat("dd/MM/yy")
            val date = Date(record.timestamp)
            val stDate = dsf.format(date)

            holder.tvFileName.text = record.filename
            holder.tvMeta.text = "${record.duration}"

            if (record.isChecked) {
                holder.itemView.setBackgroundResource(R.drawable.item_selector)
            } else {
                holder.itemView.setBackgroundResource(android.R.color.transparent)
            }

           if (editMode) {
                holder.checkBox.visibility = View.VISIBLE
                holder.checkBox.isChecked = record.isChecked
            } else {
                holder.checkBox.visibility = View.GONE
                holder.checkBox.isChecked = false
            }
        }
    }

    override fun getItemCount(): Int {
        return records.size
    }
}
