package com.example.noteadminapp

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.noteadminapp.databinding.ItemProductBinding

class ProductAdapter(
    private var productList: List<ProductWithId>,
    private val onEdit: (ProductWithId) -> Unit,
    private val onDelete: (String) -> Unit
) : ListAdapter<ProductWithId, ProductAdapter.ProductViewHolder>(ProductDiffCallback()) {

    inner class ProductViewHolder(val binding: ItemProductBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ProductWithId) {
            binding.tvTenSp.text = "Tên sp: ${item.product.tenSp}"
            binding.tvLoaiSp.text = "Loại sp: ${item.product.loaiSp}"
            binding.tvGiaSp.text = "Giá sp: ${item.product.gia}"

            // Hiển thị ảnh bằng cách giải mã chuỗi Base64
            if (item.product.image.isNotEmpty()) {
                try {
                    val imageBytes = Base64.decode(item.product.image, Base64.DEFAULT)
                    val decodedImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    binding.imageViewProduct.setImageBitmap(decodedImage)
                } catch (e: Exception) {
                    // Nếu lỗi giải mã, hiển thị ảnh mặc định
                    binding.imageViewProduct.setImageResource(R.drawable.ic_launcher_background)
                }
            } else {
                binding.imageViewProduct.setImageResource(R.drawable.ic_launcher_background)
            }

            // Nút sửa và xóa
            binding.btnEdit.setOnClickListener { onEdit(item) }
            binding.btnDelete.setOnClickListener { onDelete(item.id) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class ProductDiffCallback : DiffUtil.ItemCallback<ProductWithId>() {
    override fun areItemsTheSame(oldItem: ProductWithId, newItem: ProductWithId): Boolean {
        return oldItem.id == newItem.id
    }
    override fun areContentsTheSame(oldItem: ProductWithId, newItem: ProductWithId): Boolean {
        return oldItem == newItem
    }
}