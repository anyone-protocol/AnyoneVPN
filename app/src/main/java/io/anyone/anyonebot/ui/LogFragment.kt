package io.anyone.anyonebot.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import io.anyone.anyonebot.R
import io.anyone.anyonebot.databinding.FragmentLogBinding

class LogFragment : BottomSheetDialogFragment() {

    private lateinit var mBinding: FragmentLogBinding

    private var buffer = StringBuffer()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {

        mBinding = FragmentLogBinding.inflate(inflater, container, false)

        mBinding.tvLog.text = buffer.toString()

        mBinding.btCopyLog.setOnClickListener {
            val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip: ClipData = ClipData.newPlainText("log", mBinding.tvLog.text)

            clipboard?.setPrimaryClip(clip)

            Toast.makeText(mBinding.root.context, R.string.log_copied, Toast.LENGTH_LONG).show()
        }

        return mBinding.root
    }

    fun appendLog(logLine: String) {
        if (this::mBinding.isInitialized) {
            mBinding.tvLog.append(logLine)
            mBinding.tvLog.append("\n")
        }

        buffer.append(logLine).append("\n")
    }

    fun resetLog() {
        if (this::mBinding.isInitialized) mBinding.tvLog.text = ""
        buffer = StringBuffer()
    }
}