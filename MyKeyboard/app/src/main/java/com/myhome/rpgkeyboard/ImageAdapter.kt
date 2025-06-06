package com.myhome.rpgkeyboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

/**
 * 이미지 URL 리스트를 받아, 세로/가로 그리드 형태로 보여주고,
 * 클릭 시 콜백(클립보드 복사)을 호출하는 RecyclerView.Adapter
 */
class ImageAdapter(
    private val imageUrls: List<String>,
    private val itemClickListener: (String) -> Unit
) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivImage: ImageView = itemView.findViewById(R.id.iv_image)
        fun bind(url: String) {
            // Glide로 이미지 로딩
            Glide.with(itemView.context)
                .load(url)
                .centerCrop()
                .into(ivImage)

            itemView.setOnClickListener { itemClickListener(url) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(imageUrls[position])
    }

    override fun getItemCount(): Int = imageUrls.size
}