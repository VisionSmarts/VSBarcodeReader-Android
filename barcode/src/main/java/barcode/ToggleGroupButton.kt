//
//  ToggleGroupButton.kt
//
//  Copyright 2011-2026 Vision Smarts SRL. All rights reserved.
//

package com.visionsmarts.barcode

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet

class ToggleGroupButton(context: Context, attrs: AttributeSet) : androidx.appcompat.widget.AppCompatButton(context, attrs) {

    companion object AllButtons {
        val buttonSet : MutableSet<ToggleGroupButton> = mutableSetOf()
    }

    init {
        if (tag == "7" || tag == "32768") {
            toggle()
        }
        setOnClickListener { toggle() }
    }

    override fun onAttachedToWindow() {
        buttonSet.add(this)
        super.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        buttonSet.remove(this)
        super.onDetachedFromWindow()
    }

    private var toggleOn = false

    private fun updateVisual() {
        if (toggleOn) {
            setBackgroundColor(resources.getColor(R.color.colorPrimary))
            setTextColor(Color.WHITE)
        }
        else{
            setBackgroundColor(Color.TRANSPARENT )
            setTextColor(resources.getColor(R.color.colorPrimary))
        }
    }

    private fun toggle() {
        toggleOn = !toggleOn
        if (tag == "0") { // The ALL button sets all the others...
            buttonSet.filter { it.tag != "0"}.onEach { it.toggleOn = toggleOn; it.updateVisual() }
        }
        else { // ... and is set by all the others
            val allOn = buttonSet.filter { it.tag != "0"}.all{ it.toggleOn }
            buttonSet.find{ it.tag == "0"}?.run{ toggleOn = allOn; updateVisual() }
        }
        updateVisual()
    }

    // all tags must convert to Int
    val activeMask: Int
        get() = buttonSet.filter{ it.tag != "0" && it.toggleOn}.fold(0x0, { acc: Int, e: ToggleGroupButton -> acc or (e.tag as String).toInt() } )

}
