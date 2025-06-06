// 파일 경로: app/src/main/java/com/myhome/rpgkeyboard/SimpleStringAdapter.kt
package com.myhome.rpgkeyboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * 단순 문자열 리스트를 보여주는 RecyclerView.Adapter
 * @param data 초기 문자열 리스트
 * @param itemClickListener 각 항목을 클릭했을 때 해당 문자열을 호출
 */
class SimpleStringAdapter(
    private var data: MutableList<String>,
    private val itemClickListener: (String) -> Unit
) : RecyclerView.Adapter<SimpleStringAdapter.StringViewHolder>() {

    inner class StringViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvText: TextView = itemView.findViewById(android.R.id.text1)
        fun bind(item: String) {
            tvText.text = item
            itemView.setOnClickListener { itemClickListener(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StringViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return StringViewHolder(view)
    }

    override fun onBindViewHolder(holder: StringViewHolder, position: Int) {
        holder.bind(data[position])
    }

    override fun getItemCount(): Int = data.size

    /**
     * 외부에서 리스트 아이템이 변경되었을 때 호출합니다.
     * 예: saveToHistory(...) 또는 saveToFavorites(...) 수행 후 데이터 변경 시
     */
    fun updateData(newList: List<String>) {
        data = newList.toMutableList()
        notifyDataSetChanged()
    }
}