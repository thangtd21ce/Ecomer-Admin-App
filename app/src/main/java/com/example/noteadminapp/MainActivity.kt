package com.example.noteadminapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val productsCollection = firestore.collection("products")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ProductAdminScreen()
                }
            }
        }
    }

    @Composable
    fun ProductAdminScreen() {
        val context = LocalContext.current

        // --- 1. STATES ---
        var tenSp by remember { mutableStateOf("") }
        var loaiSp by remember { mutableStateOf("") }
        var giaSp by remember { mutableStateOf("") }
        var imageUri by remember { mutableStateOf<Uri?>(null) }

        var currentEditingId by remember { mutableStateOf<String?>(null) }
        var currentEditingBase64 by remember { mutableStateOf("") }

        var isLoading by remember { mutableStateOf(false) }
        var productList by remember { mutableStateOf<List<ProductWithId>>(emptyList()) }

        // Bộ chọn ảnh
        val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            imageUri = uri
        }

        // Lắng nghe dữ liệu Firestore realtime
        DisposableEffect(Unit) {
            val listener = productsCollection.orderBy("tenSp", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }
                    if (snapshots != null) {
                        productList = snapshots.map { doc ->
                            ProductWithId(doc.id, doc.toObject(Product::class.java))
                        }
                    }
                }
            onDispose { listener.remove() }
        }

        fun resetForm() {
            tenSp = ""
            loaiSp = ""
            giaSp = ""
            imageUri = null
            currentEditingId = null
            currentEditingBase64 = ""
        }

        fun handleSubmit() {
            if (tenSp.isBlank() || loaiSp.isBlank() || giaSp.isBlank()) {
                Toast.makeText(context, "Nhập đủ Tên, Loại, Giá!", Toast.LENGTH_SHORT).show()
                return
            }

            isLoading = true
            val gia = giaSp.toLongOrNull() ?: 0L
            var finalBase64 = currentEditingBase64

            if (imageUri != null) {
                finalBase64 = encodeImageToBase64(imageUri!!)
            }

            val product = Product(tenSp, loaiSp, gia, finalBase64)

            if (currentEditingId == null) {
                productsCollection.add(product).addOnCompleteListener {
                    isLoading = false
                    if (it.isSuccessful) {
                        Toast.makeText(context, "Đã thêm!", Toast.LENGTH_SHORT).show()
                        resetForm()
                    }
                }
            } else {
                productsCollection.document(currentEditingId!!).set(product).addOnCompleteListener {
                    isLoading = false
                    if (it.isSuccessful) {
                        Toast.makeText(context, "Đã cập nhật!", Toast.LENGTH_SHORT).show()
                        resetForm()
                    }
                }
            }
        }

        // --- 2. GIAO DIỆN ---
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dữ liệu sản phẩm", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = {
                    auth.signOut()
                    startActivity(Intent(context, LoginActivity::class.java))
                    finish()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout", tint = Color.Red)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Đăng xuất", color = Color.Red)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Thông tin sản phẩm", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = tenSp, onValueChange = { tenSp = it },
                        label = { Text("Tên sản phẩm") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = loaiSp, onValueChange = { loaiSp = it },
                        label = { Text("Loại sản phẩm") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = giaSp, onValueChange = { giaSp = it },
                        label = { Text("Giá sản phẩm") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { pickImageLauncher.launch("image/*") }) {
                            Text("Chọn ảnh")
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        if (imageUri != null) Text("Đã chọn ảnh mới", color = Color.Green, fontSize = 12.sp)
                        else if (currentEditingBase64.isNotEmpty()) Text("Đang dùng ảnh cũ", color = Color.Gray, fontSize = 12.sp)
                        else Text("Chưa có ảnh", color = Color.Gray, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { handleSubmit() }, modifier = Modifier.weight(1f)) {
                            Text(if (currentEditingId == null) "THÊM MỚI" else "CẬP NHẬT")
                        }
                        if (currentEditingId != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = { resetForm() }) { Text("Hủy", color = Color.Red) }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Danh sách sản phẩm", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

            // --- 3. LAZY COLUMN (Thay cho RecyclerView) ---
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(productList) { item ->
                    ProductItemCard(
                        item = item,
                        onEdit = {
                            currentEditingId = it.id
                            tenSp = it.product.tenSp
                            loaiSp = it.product.loaiSp
                            giaSp = it.product.gia.toString()
                            currentEditingBase64 = it.product.image
                            imageUri = null
                        },
                        onDelete = {
                            productsCollection.document(it.id).delete()
                                .addOnSuccessListener { Toast.makeText(context, "Đã xóa!", Toast.LENGTH_SHORT).show() }
                        }
                    )
                }
            }
        }

        if (isLoading) {
            Dialog(onDismissRequest = { }) { CircularProgressIndicator() }
        }
    }

    @Composable
    fun ProductItemCard(item: ProductWithId, onEdit: (ProductWithId) -> Unit, onDelete: (ProductWithId) -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val bitmap = remember(item.product.image) {
                    try {
                        val bytes = Base64.decode(item.product.image, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    } catch (e: Exception) { null }
                }

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(80.dp).background(Color.LightGray, RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(modifier = Modifier.size(80.dp).background(Color.LightGray, RoundedCornerShape(8.dp)))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text("Tên sp: ${item.product.tenSp}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Giá sp: ${item.product.gia}", color = Color.Red, modifier = Modifier.padding(top = 4.dp))
                    Text("Loại sp: ${item.product.loaiSp}", modifier = Modifier.padding(top = 4.dp))
                }

                Column {
                    IconButton(onClick = { onEdit(item) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFFF57C00))
                    }
                    IconButton(onClick = { onDelete(item) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                    }
                }
            }
        }
    }

    private fun encodeImageToBase64(uri: Uri): String {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            val maxWidth = 500
            val maxHeight = 500
            val ratio = Math.min(maxWidth.toFloat() / originalBitmap.width, maxHeight.toFloat() / originalBitmap.height)
            val width = Math.round(ratio * originalBitmap.width)
            val height = Math.round(ratio * originalBitmap.height)
            val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, true)
            val outputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}