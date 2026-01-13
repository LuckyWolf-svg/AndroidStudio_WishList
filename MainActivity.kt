package com.example.myapplication

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(color = Color.White, modifier = Modifier.fillMaxSize()) {
                    WishApp()
                }
            }
        }
    }
}

data class Wish(
    val name: String,
    val price: String? = null,
    val photoUri: String? = null,
    val isCompleted: Boolean = false
)

private fun savePhotoToInternalStorage(context: Context, uri: Uri): String {
    val contentResolver = context.contentResolver
    val inputStream = contentResolver.openInputStream(uri) ?: return ""

    val fileName = "wish_${UUID.randomUUID()}.jpg"
    val file = File(context.filesDir, fileName)

    FileOutputStream(file).use { outputStream ->
        inputStream.copyTo(outputStream)
    }

    return fileName
}

private fun loadPhotoFromInternalStorage(context: Context, fileName: String): Uri {
    val file = File(context.filesDir, fileName)
    return Uri.fromFile(file)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var wishes by remember { mutableStateOf(listOf<Wish>()) }
    var completedWishes by remember { mutableStateOf(listOf<Wish>()) }
    var isArchive by remember { mutableStateOf(false) }

    var showAddDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newPrice by remember { mutableStateOf("") }
    var newPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // Переменные для редактирования
    var editingWish by remember { mutableStateOf<Wish?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editPrice by remember { mutableStateOf("") }
    var editPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (showEditDialog) {
            editPhotoUri = uri
        } else {
            newPhotoUri = uri
        }
    }

    LaunchedEffect(Unit) {
        val all = loadAllWishes(context)
        wishes = all.filter { !it.isCompleted }
        completedWishes = all.filter { it.isCompleted }
    }

    val currentList = if (isArchive) completedWishes else wishes
    val title = if (isArchive) "Выполненные" else "Мои желания"

    fun startEditWish(wish: Wish) {
        editingWish = wish
        editName = wish.name
        editPrice = wish.price ?: ""
        editPhotoUri = wish.photoUri?.let { fileName ->
            loadPhotoFromInternalStorage(context, fileName)
        }
        showEditDialog = true
    }

    fun saveEditWish() {
        editingWish?.let { oldWish ->
            val photoFileName = editPhotoUri?.let { uri ->
                if (uri.toString().startsWith("file://")) {
                    File(uri.path!!).name
                } else {
                    savePhotoToInternalStorage(context, uri)
                }
            }

            val updatedWish = Wish(
                name = editName.trim(),
                price = editPrice.takeIf { it.isNotBlank() }?.trim(),
                photoUri = photoFileName,
                isCompleted = oldWish.isCompleted
            )

            if (oldWish.isCompleted) {
                completedWishes = completedWishes.map { if (it == oldWish) updatedWish else it }
            } else {
                wishes = wishes.map { if (it == oldWish) updatedWish else it }
            }

            saveAllWishes(context, wishes + completedWishes)
            editingWish = null
            editName = ""
            editPrice = ""
            editPhotoUri = null
            showEditDialog = false
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color.Black.copy(alpha = 0.4f),
        drawerContent = {
            ModalDrawerSheet(
                drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                drawerContainerColor = Color.White,
                drawerContentColor = Color.Black,
                modifier = Modifier.width(280.dp)
            ) {
                Spacer(Modifier.height(24.dp))

                Text(
                    text = "Меню",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 8.dp, bottom = 8.dp),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start
                )

                Divider(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    thickness = 1.dp,
                    color = Color.LightGray
                )

                NavigationDrawerItem(
                    label = {
                        Text(
                            "Мои желания",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp),
                            fontSize = 17.sp
                        )
                    },
                    selected = !isArchive,
                    onClick = {
                        isArchive = false
                        scope.launch { drawerState.close() }
                    },
                    icon = {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = null,
                            tint = if (!isArchive) Color(0xFF1976D2) else Color.Gray
                        )
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = Color(0xFFE3F2FD),
                        unselectedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                )

                NavigationDrawerItem(
                    label = {
                        Text(
                            "Выполненные",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp),
                            fontSize = 17.sp
                        )
                    },
                    selected = isArchive,
                    onClick = {
                        isArchive = true
                        scope.launch { drawerState.close() }
                    },
                    icon = {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isArchive) Color(0xFF1976D2) else Color.Gray
                        )
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = Color(0xFFE3F2FD),
                        unselectedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 80.dp),
                            textAlign = TextAlign.Start
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Меню",
                                tint = Color(0xFF1976D2)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (!isArchive) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .clickable { showAddDialog = true }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF5F5F5))
                                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    tint = Color(0xFF2196F3),
                                    modifier = Modifier.size(28.dp),
                                    contentDescription = null
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Text(
                                "Добавить желание",
                                color = Color(0xFF2196F3),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (currentList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (isArchive) "Архив пуст" else "Нет желаний\nНажмите «Добавить желание», чтобы начать",
                                color = Color.Gray,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            items(currentList) { wish ->
                                WishItem(
                                    wish = wish,
                                    context = context,
                                    isArchive = isArchive,
                                    onComplete = {
                                        val updated = wish.copy(isCompleted = true)
                                        wishes = wishes.filter { it != wish }
                                        completedWishes = completedWishes + updated
                                        saveAllWishes(context, wishes + completedWishes)
                                    },
                                    onDelete = {
                                        wishes = wishes.filter { it != wish }
                                        completedWishes = completedWishes.filter { it != wish }
                                        saveAllWishes(context, wishes + completedWishes)
                                    },
                                    onEdit = {
                                        startEditWish(wish)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        if (showAddDialog) {
            Dialog(onDismissRequest = { showAddDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    modifier = Modifier.widthIn(min = 400.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("Новое желание", fontSize = 20.sp, fontWeight = FontWeight.Bold)

                        Spacer(Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(120.dp)
                                    .padding(top = 16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(2.dp, Color(0xFF1976D2), RoundedCornerShape(12.dp))
                                        .background(Color(0xFFF5F5F5))
                                        .clickable {
                                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    newPhotoUri?.let { uri ->
                                        Image(
                                            painter = rememberAsyncImagePainter(uri),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(12.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Изменить фото",
                                            tint = Color.White,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                                .padding(4.dp)
                                        )
                                    } ?: run {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Выбрать фото",
                                            tint = Color(0xFF1976D2),
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "Фото",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            Spacer(Modifier.width(16.dp))

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newName,
                                    onValueChange = { newName = it },
                                    label = { Text("Название *") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = newPrice,
                                    onValueChange = { newPrice = it },
                                    label = { Text("Цена (необязательно)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { showAddDialog = false }) {
                                Text("Отмена")
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newName.isNotBlank()) {
                                        val photoFileName = newPhotoUri?.let { uri ->
                                            savePhotoToInternalStorage(context, uri)
                                        }

                                        val newWish = Wish(
                                            name = newName.trim(),
                                            price = newPrice.takeIf { it.isNotBlank() }?.trim(),
                                            photoUri = photoFileName
                                        )
                                        wishes = wishes + newWish
                                        saveAllWishes(context, wishes + completedWishes)
                                        newName = ""
                                        newPrice = ""
                                        newPhotoUri = null
                                        showAddDialog = false
                                    }
                                },
                                enabled = newName.isNotBlank()
                            ) {
                                Text("Добавить")
                            }
                        }
                    }
                }
            }
        }

        if (showEditDialog) {
            Dialog(onDismissRequest = {
                editingWish = null
                editName = ""
                editPrice = ""
                editPhotoUri = null
                showEditDialog = false
            }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    modifier = Modifier.widthIn(min = 400.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("Редактировать желание", fontSize = 20.sp, fontWeight = FontWeight.Bold)

                        Spacer(Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(120.dp)
                                    .padding(top = 16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(2.dp, Color(0xFF1976D2), RoundedCornerShape(12.dp))
                                        .background(Color(0xFFF5F5F5))
                                        .clickable {
                                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    editPhotoUri?.let { uri ->
                                        Image(
                                            painter = rememberAsyncImagePainter(uri),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(12.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Изменить фото",
                                            tint = Color.White,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                                .padding(4.dp)
                                        )
                                    } ?: run {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Выбрать фото",
                                            tint = Color(0xFF1976D2),
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "Фото",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            Spacer(Modifier.width(16.dp))

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = editName,
                                    onValueChange = { editName = it },
                                    label = { Text("Название *") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = editPrice,
                                    onValueChange = { editPrice = it },
                                    label = { Text("Цена (необязательно)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = {
                                editingWish = null
                                editName = ""
                                editPrice = ""
                                editPhotoUri = null
                                showEditDialog = false
                            }) {
                                Text("Отмена")
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (editName.isNotBlank()) {
                                        saveEditWish()
                                    }
                                },
                                enabled = editName.isNotBlank()
                            ) {
                                Text("Сохранить")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishItem(
    wish: Wish,
    context: Context,
    isArchive: Boolean,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val photoUri = wish.photoUri?.let { fileName ->
        loadPhotoFromInternalStorage(context, fileName)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        photoUri?.let { uri ->
            Image(
                painter = rememberAsyncImagePainter(uri),
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        } ?: Box(
            modifier = Modifier
                .size(80.dp)
                .background(Color(0xFFF0F0F0), RoundedCornerShape(12.dp))
        )

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(wish.name, fontWeight = FontWeight.Medium, fontSize = 17.sp)
            wish.price?.let { Text("$it ₽", color = Color.Gray, fontSize = 15.sp) }
        }

        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Default.MoreVert, tint = Color(0xFF2196F3), contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Изменить") },
                    onClick = {
                        onEdit()
                        expanded = false
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                )

                if (!isArchive) {
                    DropdownMenuItem(
                        text = { Text("Исполнено") },
                        onClick = {
                            onComplete()
                            expanded = false
                        },
                        leadingIcon = { Icon(Icons.Default.Check, null) }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Удалить", color = Color.Red) },
                    onClick = {
                        onDelete()
                        expanded = false
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                )
            }
        }
    }
}

private fun saveAllWishes(context: Context, all: List<Wish>) {
    val array = JSONArray()
    all.forEach { wish ->
        array.put(JSONObject().apply {
            put("name", wish.name)
            put("price", wish.price)
            put("photoFileName", wish.photoUri)
            put("isCompleted", wish.isCompleted)
        })
    }
    context.getSharedPreferences("wishes_prefs", Context.MODE_PRIVATE)
        .edit()
        .putString("all_wishes", array.toString())
        .apply()
}

private fun loadAllWishes(context: Context): List<Wish> {
    val str = context.getSharedPreferences("wishes_prefs", Context.MODE_PRIVATE)
        .getString("all_wishes", null) ?: return emptyList()
    return try {
        val array = JSONArray(str)
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            val photoFileName = if (obj.isNull("photoFileName")) null else obj.getString("photoFileName")
            Wish(
                name = obj.getString("name"),
                price = if (obj.isNull("price")) null else obj.getString("price"),
                photoUri = photoFileName,
                isCompleted = obj.optBoolean("isCompleted", false)
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}
