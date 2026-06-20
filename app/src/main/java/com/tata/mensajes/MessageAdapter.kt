package com.tata.mensajes

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tata.mensajes.databinding.ItemMessageBinding

class MessageAdapter(
    private val onRead: (Message) -> Unit,
    private val onReply: (Message) -> Unit,
    private val onCall: (Message) -> Unit
) : RecyclerView.Adapter<MessageAdapter.VH>() {

    private var items: List<Message> = emptyList()

    fun submit(list: List<Message>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(val b: ItemMessageBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val m = items[position]
        with(holder.b) {
            sender.text = "${m.sender}  ·  ${m.appLabel}"
            time.text = DateUtils.getRelativeTimeSpanString(m.time)
            messageText.text = m.text
            readButton.setOnClickListener { onRead(m) }
            val ctx = root.context
            when {
                m.call != null -> {
                    replyButton.visibility = android.view.View.VISIBLE
                    replyButton.text = ctx.getString(R.string.call)
                    replyButton.backgroundTintList =
                        ctx.getColorStateList(R.color.call_btn)
                    replyButton.setOnClickListener { onCall(m) }
                }
                m.reply != null -> {
                    replyButton.visibility = android.view.View.VISIBLE
                    replyButton.text = ctx.getString(R.string.reply)
                    replyButton.backgroundTintList =
                        ctx.getColorStateList(R.color.reply_btn)
                    replyButton.setOnClickListener { onReply(m) }
                }
                else -> replyButton.visibility = android.view.View.GONE
            }
        }
    }
}
