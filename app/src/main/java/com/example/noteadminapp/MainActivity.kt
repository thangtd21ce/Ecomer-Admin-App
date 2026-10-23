package com.example.noteadminapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.noteadminapp.databinding.ActivityMainBinding
import com.example.noteadminapp.databinding.DialogLoadingBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.io.ByteArrayOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ProductAdapter
    private lateinit var loadingDialog: AlertDialog

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val productsCollection = firestore.collection("products")

    private var snapshotListener: ListenerRegistration? = null
    private var currentEditingProductId: String? = null
    private var currentEditingImageUrl: String = ""
    private var imageUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri
            binding.imgPreview.setImageURI(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupLoadingDialog()
        setupRecyclerView()
        setupEventListeners()
        startRealtimeUpdates()
    }

    private fun setupLoadingDialog() {
        val dialogBinding = DialogLoadingBinding.inflate(layoutInflater)
        loadingDialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()
    }

    private fun setupRecyclerView() {
        adapter = ProductAdapter(emptyList(),
            onEdit = { productWithId -> showEditForm(productWithId) },
            onDelete = { id -> deleteProduct(id) }
        )
        binding.recyclerViewNotes.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewNotes.adapter = adapter
    }

    private fun setupEventListeners() {
        binding.btnLogout.setOnClickListener {
            // Hủy lắng nghe dữ liệu Firebase TRƯỚC KHI đăng xuất để tránh lỗi Permission Denied
            snapshotListener?.remove()

            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        binding.btnSelectImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnSubmit.setOnClickListener {
            handleSubmit()
        }

        binding.btnClear.setOnClickListener {
            resetForm()
        }
    }

    private fun startRealtimeUpdates() {
        snapshotListener = productsCollection.orderBy("tenSp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Toast.makeText(this, "Lỗi tải dữ liệu: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                if (snapshots != null) {
                    val productsList = snapshots.map { doc ->
                        ProductWithId(doc.id, doc.toObject(Product::class.java))
                    }
                    adapter.submitList(productsList)
                }
            }
    }

    // Hàm thu nhỏ và nén ảnh thành chuỗi ký tự (Base64) để lưu vào Firestore
    private fun encodeImageToBase64(uri: Uri): String {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)

            // Thu nhỏ ảnh xuống tối đa 500x500 pixel để tránh lỗi vượt quá 1MB của Firestore
            val maxWidth = 500
            val maxHeight = 500
            val ratio = Math.min(maxWidth.toFloat() / originalBitmap.width, maxHeight.toFloat() / originalBitmap.height)
            val width = Math.round(ratio * originalBitmap.width)
            val height = Math.round(ratio * originalBitmap.height)

            val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, true)
            val outputStream = ByteArrayOutputStream()

            // Nén JPEG chất lượng 60%
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
            val byteArray = outputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun handleSubmit() {
        val tenSp = binding.etTenSp.text.toString().trim()
        val loaiSp = binding.etLoaiSp.text.toString().trim()
        val giaStr = binding.etGiaSp.text.toString().trim()

        if (tenSp.isEmpty() || loaiSp.isEmpty() || giaStr.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đủ Tên, Loại và Giá!", Toast.LENGTH_SHORT).show()
            return
        }

        val gia = giaStr.toLongOrNull() ?: 0L
        loadingDialog.show()

        // Khởi tạo Base64 là ảnh cũ (khi sửa)
        var base64Image = currentEditingImageUrl

        // Nếu có chọn ảnh mới từ thư viện, tiến hành nén và mã hóa
        if (imageUri != null) {
            base64Image = encodeImageToBase64(imageUri!!)
        }

        saveProductToFirestore(Product(tenSp, loaiSp, gia, base64Image))
    }

    private fun saveProductToFirestore(product: Product) {
        if (currentEditingProductId == null) {
            // THÊM MỚI
            productsCollection.add(product)
                .addOnSuccessListener {
                    Toast.makeText(this, "Thêm sản phẩm thành công!", Toast.LENGTH_SHORT).show()
                    resetForm()
                    loadingDialog.dismiss()
                }
                .addOnFailureListener { e ->
                    loadingDialog.dismiss()
                    Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            // CẬP NHẬT
            productsCollection.document(currentEditingProductId!!).set(product)
                .addOnSuccessListener {
                    Toast.makeText(this, "Cập nhật thành công!", Toast.LENGTH_SHORT).show()
                    resetForm()
                    loadingDialog.dismiss()
                }
                .addOnFailureListener { e ->
                    loadingDialog.dismiss()
                    Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun deleteProduct(docId: String) {
        AlertDialog.Builder(this)
            .setTitle("Xác nhận xóa")
            .setMessage("Bạn có chắc chắn muốn xóa sản phẩm này?")
            .setPositiveButton("Xóa ngay") { _, _ ->
                productsCollection.document(docId).delete()
                    .addOnSuccessListener { Toast.makeText(this, "Đã xóa!", Toast.LENGTH_SHORT).show() }
                    .addOnFailureListener { e -> Toast.makeText(this, "Lỗi xóa: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
            .setNegativeButton("Không", null)
            .show()
    }

    private fun showEditForm(item: ProductWithId) {
        currentEditingProductId = item.id
        currentEditingImageUrl = item.product.image
        imageUri = null

        binding.etTenSp.setText(item.product.tenSp)
        binding.etLoaiSp.setText(item.product.loaiSp)
        binding.etGiaSp.setText(item.product.gia.toString())

        // Hiển thị ảnh cũ từ chuỗi Base64
        if (item.product.image.isNotEmpty()) {
            try {
                val imageBytes = Base64.decode(item.product.image, Base64.DEFAULT)
                val decodedImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                binding.imgPreview.setImageBitmap(decodedImage)
            } catch (e: Exception) {
                binding.imgPreview.setImageResource(R.drawable.ic_launcher_background)
            }
        } else {
            binding.imgPreview.setImageResource(R.drawable.ic_launcher_background)
        }

        binding.btnSubmit.text = "Lưu cập nhật"
        binding.btnClear.visibility = View.VISIBLE
        binding.formScrollView.smoothScrollTo(0, 0)
    }

    private fun resetForm() {
        currentEditingProductId = null
        currentEditingImageUrl = ""
        imageUri = null

        binding.etTenSp.text.clear()
        binding.etLoaiSp.text.clear()
        binding.etGiaSp.text.clear()
        binding.imgPreview.setImageResource(R.drawable.ic_launcher_background)

        binding.btnSubmit.text = "Thêm mới"
        binding.btnClear.visibility = View.GONE
        binding.etTenSp.requestFocus()
    }

    override fun onDestroy() {
        super.onDestroy()
        snapshotListener?.remove()
    }
}