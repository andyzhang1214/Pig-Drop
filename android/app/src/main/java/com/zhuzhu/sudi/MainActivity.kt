package com.zhuzhu.sudi

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.zhuzhu.sudi.core.FileKind
import com.zhuzhu.sudi.core.MimeMap
import com.zhuzhu.sudi.core.Record
import com.zhuzhu.sudi.core.SizeText
import com.zhuzhu.sudi.net.Lan
import com.zhuzhu.sudi.net.PhonePage
import com.zhuzhu.sudi.net.QrBitmap
import com.zhuzhu.sudi.net.UploadServer
import com.zhuzhu.sudi.store.Repository
import com.zhuzhu.sudi.store.Storage
import com.zhuzhu.sudi.ui.Draw
import com.zhuzhu.sudi.ui.FileListView
import com.zhuzhu.sudi.ui.Overlays
import com.zhuzhu.sudi.ui.Palette
import com.zhuzhu.sudi.ui.Palettes

/**
 * 电视端主界面。
 *
 * 一句话职责：把「手机扫码 → 文件落到电视 → 遥控器能打开/删掉」这条链路串起来，
 * 并且在没网、没权限、端口被占这些坏天气里也说人话。
 */
class MainActivity : Activity(), FileListView.Listener {

    private lateinit var storage: Storage
    private lateinit var repo: Repository
    private lateinit var overlays: Overlays

    private var server: UploadServer? = null
    private var serverPort = 0

    private var palette: Palette = Palettes.NOIR
    private var manageMode = false

    /** 用户点了「打开」但还没拿到安装权限的那个 APK。从设置页回来时接着装，不用他重新找。 */
    private var pendingInstall: Record? = null

    private lateinit var root: FrameLayout
    private lateinit var brandMark: ImageView
    private lateinit var brandName: TextView
    private lateinit var qrCard: FrameLayout
    private lateinit var qrImage: ImageView
    private lateinit var qrCaption: TextView
    private lateinit var urlText: TextView
    private lateinit var netHint: TextView
    private lateinit var btnSettings: TextView
    private lateinit var btnBigQr: TextView
    private lateinit var listTitle: TextView
    private lateinit var listMeta: TextView
    private lateinit var btnManage: TextView
    private lateinit var incoming: TextView
    private lateinit var listScroll: ScrollView
    private lateinit var fileList: FileListView
    private lateinit var emptyState: LinearLayout
    private lateinit var emptyMark: ImageView
    private lateinit var emptyTitle: TextView
    private lateinit var emptyBody: TextView
    private lateinit var hintRow: LinearLayout
    private lateinit var hintDpad: TextView
    private lateinit var hintRight: TextView
    private lateinit var manageBar: LinearLayout
    private lateinit var manageCount: TextView
    private lateinit var btnSelectAll: TextView
    private lateinit var btnDeleteSelected: TextView
    private lateinit var btnManageDone: TextView

    private val prefs by lazy { getSharedPreferences("zhuzhu", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = Palettes.byKey(prefs.getString("theme", Palettes.NOIR.key))

        setContentView(R.layout.activity_main)
        bindViews()

        storage = Storage(this)
        repo = Repository(this, storage)
        // 仓库的变更来自上传线程，界面更新必须回主线程——否则就是
        // "Only the original thread that created a view hierarchy can touch its views"
        repo.onChange = { runOnUiThread { renderList() } }

        overlays = Overlays(this, findViewById(R.id.overlayHost), findViewById<android.view.ViewGroup>(R.id.stage))
        overlays.palette = palette

        fileList.listener = this
        wireButtons()
        applyPalette()
        repo.load()
        renderList()
        startServer()
        refreshQr()

        // 接收文件时别让屏保跳出来打断传输
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestStoragePermissionIfNeeded()

        // 有文件就把焦点放在第一行——遥控器用户打开应用就是想看列表
        fileList.post { focusInitial() }
    }

    private fun focusInitial() {
        if (repo.records.isNotEmpty() && fileList.focusFirst()) return
        btnSettings.requestFocus()
    }

    private fun bindViews() {
        root = findViewById(R.id.root)
        brandMark = findViewById(R.id.brandMark)
        brandName = findViewById(R.id.brandName)
        qrCard = findViewById(R.id.qrCard)
        qrImage = findViewById(R.id.qrImage)
        qrCaption = findViewById(R.id.qrCaption)
        urlText = findViewById(R.id.urlText)
        netHint = findViewById(R.id.netHint)
        btnSettings = findViewById(R.id.btnSettings)
        btnBigQr = findViewById(R.id.btnBigQr)
        listTitle = findViewById(R.id.listTitle)
        listMeta = findViewById(R.id.listMeta)
        btnManage = findViewById(R.id.btnManage)
        incoming = findViewById(R.id.incoming)
        listScroll = findViewById(R.id.listScroll)
        fileList = FileListView(this, palette)
        findViewById<LinearLayout>(R.id.fileListHost).addView(
            fileList,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        emptyState = findViewById(R.id.emptyState)
        emptyMark = findViewById(R.id.emptyMark)
        emptyTitle = findViewById(R.id.emptyTitle)
        emptyBody = findViewById(R.id.emptyBody)
        hintRow = findViewById(R.id.hintRow)
        hintDpad = findViewById(R.id.hintDpad)
        hintRight = findViewById(R.id.hintRight)
        manageBar = findViewById(R.id.manageBar)
        manageCount = findViewById(R.id.manageCount)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnDeleteSelected = findViewById(R.id.btnDeleteSelected)
        btnManageDone = findViewById(R.id.btnManageDone)
    }

    // ---------- 外观 ----------

    private fun applyPalette() {
        val p = palette
        root.setBackgroundColor(p.bg)

        brandMark.setColorFilter(p.accent)
        brandName.setTextColor(p.ink)
        brandName.typeface = p.display
        brandName.letterSpacing = 0.04f

        // 二维码衬板必须比背景亮一档；浅色主题下两者接近，靠一条细描边把边界交代清楚
        qrCard.background = Draw.rect(
            p.qrBg, Draw.dpF(this, p.radiusDp * 1.1f), Draw.dp(this, 1f), p.line2
        )

        qrCaption.setTextColor(p.ink3)
        qrCaption.typeface = p.body
        urlText.setTextColor(p.ink2)
        urlText.typeface = p.mono
        netHint.setTextColor(p.ink3)
        netHint.typeface = p.body

        listTitle.setTextColor(p.ink)
        listTitle.typeface = p.display
        listMeta.setTextColor(p.ink3)
        listMeta.typeface = p.mono

        incoming.typeface = p.mono
        incoming.background = Draw.rect(Draw.withAlpha(p.accent, 0.14f), Draw.dpF(this, p.radiusDp * 0.6f))
        incoming.setTextColor(p.accent)

        emptyMark.setColorFilter(p.ink3)
        emptyMark.alpha = 0.55f
        emptyTitle.setTextColor(p.ink2)
        emptyTitle.typeface = p.display
        emptyBody.setTextColor(p.ink3)
        emptyBody.typeface = p.body

        hintDpad.setTextColor(p.ink3)
        hintDpad.typeface = p.mono
        hintRight.setTextColor(p.ink3)
        hintRight.typeface = p.mono

        manageCount.setTextColor(p.ink2)
        manageCount.typeface = p.mono

        listOf(btnSettings, btnBigQr, btnManage, btnSelectAll, btnManageDone).forEach {
            styleChip(it, p)
        }
        styleChip(btnDeleteSelected, p, danger = true)
    }

    private fun styleChip(view: TextView, p: Palette, danger: Boolean = false) {
        view.typeface = Palettes.labelTypeface()
        view.background = Draw.states(
            normalFill = Color.TRANSPARENT,
            radiusPx = Draw.dpF(this, p.radiusDp * 0.7f),
            lineColor = if (danger) Draw.withAlpha(p.danger, 0.55f) else p.line2,
            linePx = Draw.dp(this, 1f),
            focusFill = if (danger) Draw.withAlpha(p.danger, 0.16f) else p.surface2,
            focusLine = if (danger) p.danger else p.accent,
            focusLinePx = Draw.dp(this, 2f)
        )
        view.setTextColor(if (danger) p.danger else p.ink2)
        view.isFocusable = true
        view.isClickable = true
        view.isFocusableInTouchMode = true
    }

    // ---------- 服务 ----------

    private fun startServer() {
        server = UploadServer.startOnFreePort(
            context = this,
            port = DEFAULT_PORT,
            repo = repo,
            storage = storage,
            palette = { palette },
            brand = { getString(R.string.app_name) },
            page = { PhonePage.build(this, baseUrl(), palette, getString(R.string.app_name), repo.records) },
            onUploaded = { record -> runOnUiThread { onReceived(record) } },
            onProgress = { name, sent, total -> runOnUiThread { showIncoming(name, sent, total) } }
        )
        serverPort = server?.listeningPort ?: 0
    }

    private fun baseUrl(): String =
        if (serverPort > 0) Lan.baseUrl(serverPort) else "http://${Lan.ipv4() ?: "127.0.0.1"}:$DEFAULT_PORT"

    private fun refreshQr() {
        val url = baseUrl()
        if (serverPort <= 0) {
            qrImage.setImageDrawable(null)
            qrCard.alpha = 0.35f
            urlText.text = "服务没起来"
            netHint.text = "端口 $DEFAULT_PORT 起被占用了，换个网络或重启电视再试"
            netHint.visibility = View.VISIBLE
            return
        }
        qrCard.alpha = 1f
        urlText.text = url
        qrImage.setImageBitmap(
            QrBitmap.render(url, Draw.dp(this, 200f), palette.qrFg, palette.qrBg)
        )
        netHint.text = "手机和电视连同一个 Wi-Fi\n文件存到 ${storage.location().label}"
        netHint.visibility = View.VISIBLE
    }

    private fun showIncoming(name: String, sent: Long, total: Long) {
        if (total <= 0) return
        val percent = (sent * 100 / total).toInt().coerceIn(0, 100)
        incoming.visibility = View.VISIBLE
        incoming.text = "正在接收 $name · $percent% · ${SizeText.of(sent)} / ${SizeText.of(total)}"
        incoming.postDelayed({
            if (incoming.text.contains(name) && percent >= 100) incoming.visibility = View.GONE
        }, 1600)
    }

    private fun onReceived(record: Record) {
        renderList()
        listScroll.smoothScrollTo(0, 0)
        // 用列表上方的横条确认，不用 Toast：电视上的 Toast 会压在列表上，还看不清
        incoming.visibility = View.VISIBLE
        incoming.text = "已收到 ${record.name}"
        incoming.postDelayed({
            if (incoming.text.contains(record.name)) incoming.visibility = View.GONE
        }, 2400)
    }

    // ---------- 列表 ----------

    private fun renderList() {
        val records = repo.records
        fileList.render(records, manageMode)

        val empty = records.isEmpty()
        emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        listScroll.visibility = if (empty) View.GONE else View.VISIBLE
        btnManage.visibility = if (empty || manageMode) View.GONE else View.VISIBLE

        // 空列表时这块统计没有意义：「已接收 空」既不是数字也不是句子。
        // 空状态下面已经写清楚了，这里直接不显示。
        listMeta.text = if (empty) "" else "${records.size} 个文件 · ${SizeText.totalOf(repo.totalBytes())}"
        listMeta.visibility = if (empty) View.GONE else View.VISIBLE
        emptyBody.text = "手机扫左边的二维码，随便传点什么过来\n文件会存到 ${storage.location().label}"
        updateManageUi()
    }

    private fun updateManageUi() {
        hintRow.visibility = if (manageMode) View.GONE else View.VISIBLE
        manageBar.visibility = if (manageMode) View.VISIBLE else View.GONE

        val selected = fileList.selectedRecords()
        val count = selected.size
        manageCount.text = if (count == 0) {
            "未选择"
        } else {
            "已选 $count 个 · ${SizeText.totalOf(selected.sumOf { it.size })}"
        }
        btnDeleteSelected.isEnabled = count > 0
        btnDeleteSelected.alpha = if (count > 0) 1f else 0.4f
        btnDeleteSelected.isFocusable = count > 0
        btnSelectAll.text = if (count == repo.records.size && count > 0) "取消全选" else "全选"
    }

    // ---------- 交互 ----------

    private fun wireButtons() {
        btnSettings.setOnClickListener { showSettings() }
        btnBigQr.setOnClickListener { showBigQr() }
        btnManage.setOnClickListener { enterManage() }
        btnManageDone.setOnClickListener { exitManage() }
        btnSelectAll.setOnClickListener { toggleSelectAll() }
        btnDeleteSelected.setOnClickListener { confirmDeleteSelected() }

        btnDeleteSelected.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus && !v.isEnabled) fileList.rowAt(0)?.requestFocus()
        }

        // 管理模式下右键直达操作条，左键要能回到列表——否则焦点会卡在操作条出不来
        fileList.manageExitTarget = btnSelectAll
        fileList.leftExitTarget = btnSettings
        btnSelectAll.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT
            ) {
                fileList.focusFirst()
                true
            } else {
                false
            }
        }
    }

    private fun showBigQr() {
        if (serverPort <= 0) return
        val size = Draw.dp(this, 380f)
        overlays.rememberFocus(currentFocus)
        overlays.showBigQr(
            QrBitmap.render(baseUrl(), size, palette.qrFg, palette.qrBg),
            baseUrl()
        )
    }

    private fun showSettings() {
        overlays.rememberFocus(currentFocus)
        overlays.showSettings(
            port = serverPort,
            url = baseUrl(),
            locationLabel = storage.location().label,
            onPickTheme = { key ->
                if (key != palette.key) {
                    prefs.edit().putString("theme", key).apply()
                    recreate()
                }
            },
            onClearAll = { confirmClearAll() }
        )
    }

    private fun enterManage() {
        if (repo.records.isEmpty()) return
        manageMode = true
        fileList.render(repo.records, true, keepSelection = false)
        renderList()
        fileList.focusFirst()
    }

    private fun exitManage() {
        manageMode = false
        fileList.render(repo.records, false, keepSelection = false)
        renderList()
        fileList.focusFirst()
    }

    private fun toggleSelectAll() {
        val all = repo.records
        val selected = fileList.selectedRecords()
        if (selected.size == all.size) {
            fileList.selectedIds.clear()
        } else {
            fileList.selectedIds.clear()
            all.forEach { fileList.selectedIds.add(it.id) }
        }
        fileList.render(all, true)
        updateManageUi()
    }

    private fun confirmDeleteSelected() {
        val selected = fileList.selectedRecords()
        if (selected.isEmpty()) return
        overlays.rememberFocus(currentFocus)
        overlays.showConfirm(
            title = if (selected.size == 1) "删除这个文件？" else "删除这 ${selected.size} 个文件？",
            body = buildString {
                append(selected.take(3).joinToString("、") { it.name })
                if (selected.size > 3) append(" 等 ${selected.size} 个文件")
                append("\n删除后电视上就找不回来了。")
            },
            confirmLabel = if (selected.size == 1) "删除" else "全部删除"
        ) {
            val count = repo.remove(selected.map { it.id })
            manageMode = false
            renderList()
            fileList.focusFirst()
            Toast.makeText(this, "已删除 $count 个文件", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmClearAll() {
        val all = repo.records
        if (all.isEmpty()) {
            Toast.makeText(this, "本来就没有文件", Toast.LENGTH_SHORT).show()
            return
        }
        overlays.rememberFocus(currentFocus)
        overlays.showConfirm(
            title = "清空全部记录？",
            body = "将删除 ${all.size} 个文件（${SizeText.totalOf(repo.totalBytes())}），\n包括电视下载目录里的原文件。",
            confirmLabel = "全部删除"
        ) {
            val count = repo.clear()
            manageMode = false
            renderList()
            Toast.makeText(this, "已清空 $count 个文件", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- FileListView.Listener ----------

    override fun onActivate(record: Record) = openRecord(record)

    override fun onOpen(record: Record) = openRecord(record)

    override fun onDelete(record: Record) {
        overlays.rememberFocus(currentFocus)
        overlays.showConfirm(
            title = "删除这个文件？",
            body = "${record.name}\n删除后电视上就找不回来了。",
            confirmLabel = "删除"
        ) {
            val index = repo.records.indexOfFirst { it.id == record.id }
            repo.remove(listOf(record.id))
            if (manageMode) {
                fileList.render(repo.records, true)
                updateManageUi()
            } else {
                renderList()
                if (!fileList.focusNear(index)) btnSettings.requestFocus()
            }
        }
    }

    override fun onSelectionChanged() = updateManageUi()

    private fun openRecord(record: Record) {
        if (manageMode) return
        // APK 先自己看一眼「安装未知应用」开没开。没开时照样发 intent 的话，用户拿到的是
        // PackageInstaller 弹的全英文对话框（For your security, your TV currently isn't
        // allowed to install unknown apps...），中文用户既看不懂也不知道该点哪个，
        // 表现就是「传到电视上装不了」。这里提前拦住，用中文说清去哪开。
        if (MimeMap.kindOf(record.name) == FileKind.APK && !canInstallApk()) {
            pendingInstall = record
            overlays.rememberFocus(currentFocus)
            overlays.showInstallBlocked(
                onOpenSettings = { openUnknownAppSources() },
                onCancel = { pendingInstall = null }
            )
            return
        }
        pendingInstall = null
        val uri = storage.accessUri(record)
        if (uri == null) {
            overlays.rememberFocus(currentFocus)
            overlays.showOpenFailed(record, "文件已经不在电视上了，可能被删掉或挪走了。")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MimeMap.mimeOf(record.name))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // 必须先问「有没有应用能接」：没有的话 startActivity 不抛异常，
        // 系统只会弹一句英文 toast（"You don't have an app that can do this"），
        // 用户看不懂也不知道下一步干什么。这里自己接管，给中文的下一步。
        if (!hasRealHandler(intent)) {
            overlays.rememberFocus(currentFocus)
            overlays.showOpenFailed(record, adviceFor(record))
            return
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            overlays.rememberFocus(currentFocus)
            overlays.showOpenFailed(record, adviceFor(record))
        }
    }

    /**
     * 能不能装 APK？
     *
     * Android 8.0 起「安装未知应用」是按应用单独授权的，必须问 `canRequestPackageInstalls`；
     * 7.x 及以下只有系统级的「未知来源」总开关，应用查不到也没有权限改，只能放行让系统判断。
     */
    private fun canInstallApk(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return packageManager.canRequestPackageInstalls()
    }

    /** 跳到系统里给本应用授权「安装未知应用」的那一页。 */
    private fun openUnknownAppSources() {
        val target = Uri.parse("package:$packageName")
        val direct = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, target)
        try {
            startActivity(direct)
            return
        } catch (e: Exception) {
            // 有些电视固件把这一页裁掉了，退一步到应用详情页
        }
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, target))
        } catch (e: Exception) {
            pendingInstall = null
            Toast.makeText(
                this,
                "这台电视没有开放的安装设置页，试试把 APK 拷到 U 盘再装",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // 从「安装未知应用」页面回来：如果开关已经打开，就把刚才那次「打开」接着做完。
        // 没打开就什么都不做——用户可能还在设置里翻，别拿浮层打断他。
        val pending = pendingInstall ?: return
        if (!canInstallApk()) return
        pendingInstall = null
        openRecord(pending)
    }

    /**
     * 真的有应用能接这个 intent 吗？
     *
     * 只用 `resolveActivity` 判断不够。AOSP 电视镜像里自带一个
     * `com.android.tv.frameworkpackagestubs` 的占位 Activity，什么类型都接、接住之后
     * 立刻退出，于是用户按「打开」看到的是**完全没反应**——比弹一句「没有应用能打开」
     * 更让人摸不着头脑。这里把占位包和本应用自己排除掉再判断。
     */
    private fun hasRealHandler(intent: Intent): Boolean {
        val flags = PackageManager.MATCH_DEFAULT_ONLY
        val handlers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, flags)
        }
        return handlers.any { info ->
            val pkg = info.activityInfo?.packageName
            pkg != null && pkg != packageName && pkg !in STUB_PACKAGES
        }
    }

    /**
     * 打开失败时给下一步，不是一句「打开失败」。
     *
     * 建议必须**按类型给**：早先所有类型共用一句「拷到 U 盘，或删掉腾出空间」，
     * 对着一个 332 KB 的图片说「腾空间」是答非所问——用户真正想知道的是
     * 「我就是想在电视上看这张图，该怎么办」。
     */
    private fun adviceFor(record: Record): String {
        val ext = MimeMap.extensionOf(record.name)
        return when (MimeMap.kindOf(record.name)) {
            FileKind.APK ->
                // 权限问题已经在 openRecord 入口拦掉了，走到这里只剩「系统里根本没有安装器」
                "这台电视没有能装 APK 的入口。\n有些电视只允许从自带商店或 U 盘安装，可以去「设置 → 应用」里找找。"
            FileKind.ARCHIVE ->
                "压缩包一般没有应用能直接打开。\n拷到 U 盘，在电脑上解压。"
            FileKind.VIDEO -> "电视上没有能播放 .$ext 的应用。\n装一个播放器就能直接看，或者拷到 U 盘在电脑上放。"
            FileKind.IMAGE -> "电视上没有能看图的应用。\n装一个看图应用，或者把图拷到 U 盘在电脑上看。"
            FileKind.AUDIO -> "电视上没有能放 .$ext 的应用。\n装一个音乐播放器，或者拷到 U 盘在别的设备上听。"
            FileKind.DOC -> "电视上没有能打开 .$ext 的应用。\n拷到 U 盘用电脑打开更顺手。"
            FileKind.OTHER -> "电视上不确定该用什么打开 .$ext。\n可以拷到 U 盘，或者直接在手机上看。"
        }
    }

    // ---------- 权限与生命周期 ----------

    private fun requestStoragePermissionIfNeeded() {
        if (!storage.requiresPermission()) return
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_STORAGE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_STORAGE) return
        // 拿不到权限也不挡使用：退回应用私有目录，界面上如实说文件在哪儿
        refreshQr()
        Toast.makeText(
            this,
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) "文件会存到电视的下载文件夹"
            else "没有存储权限，文件改存到应用私有目录",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onBackPressed() {
        if (overlays.dismiss()) return
        if (manageMode) {
            exitManage()
            return
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        super.onDestroy()
    }

    companion object {
        private const val DEFAULT_PORT = 8080
        private const val REQ_STORAGE = 1001
        // 对外品牌名统一取 @string/app_name：电视界面、手机上传页、桌面图标名都来自这一处。
        // 落盘目录名是另一回事，仍是 ASCII 的 Storage.FOLDER。

        /** 只会「接住就退出」的占位包，不能算作能打开文件的应用。 */
        private val STUB_PACKAGES = setOf("com.android.tv.frameworkpackagestubs")
    }
}
