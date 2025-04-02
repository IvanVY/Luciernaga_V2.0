package com.example.luciernaga

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EventLogAdapter : RecyclerView.Adapter<EventLogAdapter.EventLogViewHolder>() {

    private var eventLogList: List<String> = emptyList()

    // ViewHolder para cada elemento del RecyclerView
    class EventLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val eventTextView: TextView = itemView.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventLogViewHolder {
        // Infla el diseño de cada elemento (usamos un diseño simple de Android)
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return EventLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventLogViewHolder, position: Int) {
        // Asigna los datos al TextView
        holder.eventTextView.text = eventLogList[position]
    }

    override fun getItemCount(): Int = eventLogList.size

    // Método para actualizar los datos del adaptador
    fun updateData(newData: List<String>) {
        eventLogList = newData
        notifyDataSetChanged() // Notifica al RecyclerView que los datos han cambiado
    }
}