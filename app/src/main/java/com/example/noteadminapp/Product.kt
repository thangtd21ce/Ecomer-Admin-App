package com.example.noteadminapp

data class Product(
    var tenSp: String = "",
    var loaiSp: String = "",
    var gia: Long = 0L,
    var image: String = ""
)

data class ProductWithId(
    val id: String,
    val product: Product
)