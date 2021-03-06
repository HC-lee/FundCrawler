package com.supcoder.fundcrawler

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.design.widget.BottomSheetDialog
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.text.Editable
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.PopupWindow
import com.bumptech.glide.Glide
import com.chad.library.adapter.base.callback.ItemDragAndSwipeCallback
import com.supcoder.fundcrawler.adapter.BottomSheetAdapter
import com.supcoder.fundcrawler.adapter.HistoryAdapter
import com.supcoder.fundcrawler.adapter.ImageAdapter
import com.supcoder.fundcrawler.entity.HistoryEntity
import com.supcoder.fundcrawler.entity.MoneyEntity
import com.supcoder.fundcrawler.http.HtmlParserUtil
import com.supcoder.fundcrawler.http.ProcessMessege
import com.supcoder.fundcrawler.utils.ListDataSave
import com.supcoder.fundcrawler.widget.ItemDecoration
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import me.foji.realmhelper.RealmHelper
import kotlin.properties.Delegates


open class MainActivity : AppCompatActivity() {

    private var mData: MutableList<String>? = null
    private lateinit var mAdapter: ImageAdapter

    private var mHisData: MutableList<HistoryEntity>? = null
    private lateinit var mHisAdapter: HistoryAdapter

    private var mPopupWindow: PopupWindow? = null

    private var mLastClickBack: Long = 0

    private var mRealmHelper: RealmHelper by Delegates.notNull()

    private var hisRecyclerView: RecyclerView? = null

    /** sp保存list */
    private var dataSave: ListDataSave? = null

    /** 是否正在加载数据 */
    private var isLoading = false

    /** 基金走势底部弹框 */
    private var bottomDialog: BottomSheetDialog? = null
    /** 基金走势底部弹框的RecyclerView */
    private var bottomAdapter: BottomSheetAdapter? = null
    private var moneyEt: EditText? = null
    private var saveBtn: Button? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        initData()
        initRecyclerView()
        initPopupWindow()
        initBottomDialog()
        initSwipeRefreshLayout()
        bindEvent()
    }


    /**
     * 初始化数据
     */
    private fun initData() {
        mRealmHelper = RealmHelper.get(this)
        dataSave = ListDataSave(applicationContext, "fund")
        mData = dataSave!!.getDataList<String>("fundList") as MutableList<String>
    }


    /**
     * 初始化RecyclerView
     */
    private fun initRecyclerView() {

        mAdapter = ImageAdapter(mData)
        mAdapter.openLoadAnimation()

        mAdapter.setOnItemChildClickListener { adapter, view, position ->
            Log.e("Kotlin", "响应了子控件点击事件")
            if (adapter.data.size > position) {
                val fundId = adapter.data[position] as String
                Log.e("Kotlin", fundId)
                if (!isLoading) {
                    Thread(Runnable {
                        isLoading = true
                        val mList = HtmlParserUtil.getInstance().queryProcessInfo2(fundId)
                        runOnUiThread {
                            if (!isFinishing) {
                                isLoading = false
                                openBottom(mList, fundId)
                            }
                        }
                    }).start()
                } else {
                    showSnackBar("上一个弹窗还没加载完")
                }
            }
        }


        val itemDragAndSwipeCallback = ItemDragAndSwipeCallback(mAdapter)
        val itemTouchHelper = ItemTouchHelper(itemDragAndSwipeCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
        mAdapter.enableDragItem(itemTouchHelper, R.id.fundImg, true)
        mAdapter.enableSwipeItem()

        recyclerView.adapter = mAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.addItemDecoration(ItemDecoration())


        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {

            override fun onScrollStateChanged(recyclerView: RecyclerView?, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)

                hideKeyboard(editText)
            }
        })


    }


    private fun initPopupWindow() {
        mPopupWindow = PopupWindow(this)


        mPopupWindow?.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            contentView = LayoutInflater.from(this@MainActivity).inflate(R.layout.pop_history, null)
            setBackgroundDrawable(ColorDrawable(0x00000000))
            isOutsideTouchable = false
            isFocusable = true
        }

        hisRecyclerView = mPopupWindow!!.contentView.findViewById(R.id.hisRecyclerView)


        mHisAdapter = HistoryAdapter(mHisData, HistoryAdapter.OnHistoryListener { fund ->
            mRealmHelper.use {
                val hisRecord = where(HistoryEntity::class.java)
                        .equalTo("fundId",
                                fund!!.fundId
                        ).findAll()
                executeTransaction {
                    hisRecord!!.deleteAllFromRealm()
                    refreshHis()
                }
            }
        })
        mHisAdapter.setOnItemClickListener { adapter, view, position ->

            val hisEntity = adapter.data[position] as HistoryEntity
            val fundId = hisEntity.fundId
            editText.setText(fundId)
            editText.setSelection(fundId.length)
            hideKeyboard(editText)
            mPopupWindow?.dismiss()
        }

        hisRecyclerView?.apply {
            adapter = mHisAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        recyclerView.addItemDecoration(ItemDecoration())
        refreshHis()
        mPopupWindow?.setOnDismissListener { hideKeyboard(editText) }
    }


    /**
     *  初始化SwipeRefreshLayout
     */
    private fun initSwipeRefreshLayout() {
        swipeRefreshLayout?.apply {
            setColorSchemeResources(R.color.red)
            isEnabled = false
        }
    }

    private fun refreshHis() {
        mRealmHelper.use {
            val hisRecords = where(HistoryEntity::class.java).findAll()
            Log.e("KOTLIN", hisRecords.size.toString())
            if (null != hisRecords) {
                mHisAdapter.refresh(hisRecords)
                mHisAdapter.notifyDataSetChanged()

            }
        }
    }


    private fun initBottomDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_bottom, null)
        val recyclerView = view.findViewById<RecyclerView>(R.id.bottomRecycler)
        moneyEt = view.findViewById(R.id.moneyEt)
        saveBtn = view.findViewById(R.id.saveBtn)

        val mList: List<ProcessMessege>? = null
        bottomAdapter = BottomSheetAdapter(mList)

        recyclerView?.apply {
            itemAnimator = DefaultItemAnimator()
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = bottomAdapter
        }


        bottomDialog = BottomSheetDialog(this)
        bottomDialog?.setContentView(view)
    }


    private fun bindEvent() {

        addImg.setOnClickListener {
            val editTextStr = editText.text.toString().trim()
            if ("" != editTextStr) {
                if (mAdapter.data.contains(editText.text.toString().trim())) {
                    showSnackBar(editTextStr + " 号基金已存在")
                } else {
                    mAdapter.addData(0, editTextStr)
                    recyclerView.scrollToPosition(0)

                    mRealmHelper.use {
                        executeTransactionAsync({ realm ->
                            val hisEntity = HistoryEntity()
                            hisEntity.fundId = editTextStr
                            realm.copyToRealmOrUpdate(hisEntity)
                        }, {
                            refresh()
                        }, { error ->
                            showSnackBar("Insert fail : $error")
                        })
                    }
                }
                editText.text.clear()
            } else {
                showSnackBar("基金号不能为空")
            }
        }


        historyImg.setOnClickListener {
            if (mPopupWindow != null) {
                hideKeyboard(editText)
                mPopupWindow!!.showAsDropDown(toolbar)
            }
        }

        cleanImg.setOnClickListener {
            swipeRefreshLayout.isRefreshing = true
            Thread(Runnable {
                Glide.get(application).clearDiskCache()
                runOnUiThread {
                    Glide.get(application).clearMemory()
                    showSnackBar("缓存清理成功")
                    swipeRefreshLayout.isRefreshing = false
                }
            }).start()
        }


    }


    override fun onBackPressed() {
        val current = System.currentTimeMillis()
        if (current - mLastClickBack > 3 * 1000) {
            mLastClickBack = System.currentTimeMillis()
            showSnackBar("再按一次返回键退出程序")

            if (dataSave != null && mAdapter != null) {
                dataSave!!.setDataList("fundList", mAdapter.data)
            }
            return
        }
        finish()
        super.onBackPressed()
    }


    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
    }


    private fun showSnackBar(hint: String) {
        val snackBar = Snackbar.make(recyclerView, hint, 3000)
        snackBar.view.setBackgroundColor(ContextCompat.getColor(this, R.color.colorAccent))
        snackBar.show()
    }


    /**
     * 展示BottomDialog
     */
    private fun openBottom(list: List<ProcessMessege>, fundId: String) {
        bottomDialog?.let {
            it.dismiss()
            val money = getFundMoney(fundId)
            moneyEt?.apply {
                setText(money)
                setSelection(money.length)
            }

            saveBtn?.setOnClickListener {
                if (!TextUtils.isEmpty(moneyEt!!.text.toString())) {
                    saveMoney(fundId, moneyEt!!.text.toString())
                    refreshBottomDialog(list, fundId, moneyEt!!.text.toString())
                } else {
                    showSnackBar("输入金额不能为空")
                }
            }
            refreshBottomDialog(list, fundId, money)
            it.show()
        }
    }


    private fun refreshBottomDialog(list: List<ProcessMessege>, fundId: String, money: String) {
        if (!TextUtils.isEmpty(getFundMoney(fundId))) {
            for (bean in list) {
                if (bean.processName == "888888") {
                    Log.e("888888", bean.toString())
                    bean.fluctuate = HtmlParserUtil.getInstance().mul(money, bean.processScale).toString()
//                    bean.fluctuate = money
                }
            }
        }
        bottomAdapter!!.refresh(list)
    }


    private fun getFundMoney(fundId: String): String {
        var moneyEntity: MoneyEntity? = null
        mRealmHelper.use {
            val moneyRecord = where(MoneyEntity::class.java)
                    .equalTo("fundId", fundId
                    ).findAll()

            if (moneyRecord != null && moneyRecord.size > 0) {
                moneyEntity = moneyRecord[0] as MoneyEntity
            }
        }

        return if (moneyEntity != null) {
            moneyEntity!!.money
        } else {
            ""
        }
    }

    private fun saveMoney(fundId: String, money: String) {
        mRealmHelper.use {
            executeTransaction { realm ->
                val moneyEntity = MoneyEntity()
                moneyEntity.fundId = fundId
                moneyEntity.money = money
                realm.copyToRealmOrUpdate(moneyEntity)
            }

        }
    }
}
