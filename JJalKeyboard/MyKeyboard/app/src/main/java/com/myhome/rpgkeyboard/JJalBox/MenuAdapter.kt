package com.myhome.rpgkeyboard.JJalBox

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.myhome.rpgkeyboard.R

class MenuAdapter(
    private val items: List<String>,
    private val onClick: (item: String, position: Int) -> Unit,
    initialSelected: Int = 0
) : RecyclerView.Adapter<MenuAdapter.VH>() {

    // 초기 선택 위치를 생성자 값으로 설정
    private var selectedPosition = initialSelected

    inner class VH(val tv: TextView) : RecyclerView.ViewHolder(tv) {
        init {
            tv.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onClick(items[pos], pos)
                    selectedPosition = pos
                    notifyDataSetChanged()
                }
            }
        }
    }

    /** 외부에서 선택 상태를 바꿀 때 호출 */
    fun setSelectedPosition(pos: Int) {
        selectedPosition = pos
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = LayoutInflater.from(parent.context)
            .inflate(R.layout.menu_item, parent, false) as TextView
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val tv = holder.tv

        // ① 검색(0), 최근(1)은 아이콘만 표시
        when (position) {
            0 -> {
                tv.text = ""
                tv.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_search_24dp, 0, 0, 0
                )
            }

//            1 -> {
//                tv.text = ""
//                tv.setCompoundDrawablesWithIntrinsicBounds(
//                    R.drawable.ic_recent, 0, 0, 0
//                )
//            }

            else -> {
                tv.text = items[position]
                tv.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            }
        }

        // ② 선택된 항목만 파란색, 그 외 기본 색
        val colorRes = if (position == selectedPosition) R.color.blue_500
        else R.color.text_primary
        val color = ContextCompat.getColor(tv.context, colorRes)
        tv.setTextColor(color)
    }
}