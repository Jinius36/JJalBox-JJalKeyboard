import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.myhome.rpgkeyboard.R

class MenuAdapter(
    private val items: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<MenuAdapter.VH>() {

    // 초기 선택은 첫 번째(“인기”)
    private var selectedPosition = 0

    inner class VH(val tv: TextView) : RecyclerView.ViewHolder(tv) {
        init {
            tv.setOnClickListener {
                val prev = selectedPosition
                selectedPosition = adapterPosition
                notifyItemChanged(prev)
                notifyItemChanged(selectedPosition)
                onClick(items[adapterPosition])
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = LayoutInflater.from(parent.context)
            .inflate(R.layout.menu_item, parent, false) as TextView
        return VH(tv)
    }

    override fun getItemCount(): Int = items.size

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.tv.text = items[position]
        // 선택된 항목만 파란색
        if (position == selectedPosition) {
            holder.tv.setTextColor(holder.tv.context.getColor(R.color.blue_500))
        } else {
            holder.tv.setTextColor(holder.tv.context.getColor(R.color.text_primary))
        }
    }
}