import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.myhome.rpgkeyboard.R

class ImageAdapter(
    private var urls: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<ImageAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.image_view)
        init {
            view.setOnClickListener { onClick(urls[adapterPosition]) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val root = LayoutInflater.from(parent.context)
            .inflate(R.layout.image_item, parent, false)
        return VH(root)
    }

    override fun getItemCount(): Int = urls.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        Glide.with(holder.imageView.context)
            .load(urls[position])
            .centerCrop()
            .into(holder.imageView)
    }

    fun updateData(newUrls: List<String>) {
        urls = newUrls
        notifyDataSetChanged()
    }
}