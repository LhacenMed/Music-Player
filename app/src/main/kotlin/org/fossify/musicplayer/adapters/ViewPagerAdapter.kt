package org.fossify.musicplayer.adapters

import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.musicplayer.activities.SimpleActivity
import org.fossify.musicplayer.databinding.*
import org.fossify.musicplayer.extensions.getTabLabel
import org.fossify.musicplayer.extensions.getTintedTextColor
import org.fossify.musicplayer.extensions.getVisibleTabs
import org.fossify.musicplayer.fragments.MyViewPagerFragment
import org.fossify.musicplayer.fragments.TracksFragment
import org.fossify.musicplayer.helpers.*

class ViewPagerAdapter(val activity: SimpleActivity) : PagerAdapter() {
    private val fragments = arrayListOf<MyViewPagerFragment>()
    private val items = SparseArray<MyViewPagerFragment>()
    private var primaryItem: MyViewPagerFragment? = null

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val tab = activity.getVisibleTabs()[position]
        val layoutInflater = activity.layoutInflater
        val fragment =  when (tab) {
            TAB_FOLDERS -> FragmentFoldersBinding.inflate(layoutInflater, container, false).root
            TAB_ARTISTS -> FragmentArtistsBinding.inflate(layoutInflater, container, false).root
            TAB_ALBUMS -> FragmentAlbumsBinding.inflate(layoutInflater, container, false).root
            TAB_TRACKS -> FragmentTracksBinding.inflate(layoutInflater, container, false).root
            TAB_GENRES -> FragmentGenresBinding.inflate(layoutInflater, container, false).root
            else -> throw IllegalArgumentException("Unknown tab: $tab")
        }

        return fragment.apply {
            fragments.add(this)
            items.put(position, this)
            container.addView(this)
            setupFragment(activity)
            setupColors(activity.getTintedTextColor(), activity.getProperPrimaryColor())
        }
    }

    override fun destroyItem(container: ViewGroup, position: Int, item: Any) {
        fragments.remove(item)
        items.remove(position)
        container.removeView(item as View)
    }

    override fun setPrimaryItem(container: ViewGroup, position: Int, `object`: Any) {
        primaryItem = `object` as MyViewPagerFragment
    }

    override fun getCount() = activity.getVisibleTabs().size

    /** What the tab layout labels its tabs with once it is set up with the pager. */
    override fun getPageTitle(position: Int) = activity.getTabLabel(activity.getVisibleTabs()[position])

    override fun isViewFromObject(view: View, item: Any) = view == item

    fun getAllFragments() = fragments

    fun getCurrentFragment() = primaryItem

    fun getFragmentAt(position: Int): MyViewPagerFragment? = items.get(position)

    fun getTracksFragment() = fragments.find { it is TracksFragment }
}
