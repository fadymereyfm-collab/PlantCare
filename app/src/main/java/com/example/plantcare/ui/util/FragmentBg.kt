package com.example.plantcare.ui.util

import androidx.core.util.Consumer
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Callable

/**
 * Java-friendly replacement for `new Thread(...).start()` inside Fragments / DialogFragments.
 *
 * Ties the background work to `viewLifecycleOwner.lifecycleScope` when a view exists,
 * otherwise falls back to the fragment's own lifecycleScope. In both cases the
 * coroutine is cancelled automatically when the owner is destroyed, so we never
 * touch a detached view.
 *
 * Usage from Java:
 * ```
 *  FragmentBg.runIO(this, () -> {
 *      // IO work (Room, files, network)
 *  });
 *
 *  FragmentBg.runIO(this,
 *      () -> heavyWork(),           // on Dispatchers.IO
 *      () -> updateUi()             // on Dispatchers.Main (only if fragment still added)
 *  );
 *
 *  FragmentBg.<List<Plant>>runWithResult(this,
 *      () -> dao.getAll(),          // produces T on IO
 *      list -> adapter.submit(list) // consumes T on Main
 *  );
 * ```
 *
 * **Why**: `new Thread` has no lifecycle link, no cancellation, no backpressure.
 * After a configuration change the old thread can still try to mutate a detached
 * fragment, producing `IllegalStateException: Fragment not attached`. The coroutine
 * scope solves this without ceremony.
 */
object FragmentBg {

    private fun ownerOf(fragment: Fragment): LifecycleOwner =
        fragment.viewLifecycleOwnerLiveData.value ?: fragment

    /** Run `io` on Dispatchers.IO, no UI callback. */
    @JvmStatic
    fun runIO(fragment: Fragment, io: Runnable) {
        ownerOf(fragment).lifecycleScope.launch(Dispatchers.IO) {
            try {
                io.run()
            } catch (c: CancellationException) {
                // #3 fix: cooperate with structured concurrency. Without
                // this re-throw, lifecycle-driven cancellation looked
                // like an IO error in Crashlytics + the body kept
                // running past its lifecycle owner. Same pattern in
                // every catch (Throwable) below.
                throw c
            } catch (t: Throwable) {
                com.example.plantcare.CrashReporter.log(t)
            }
        }
    }

    /** Run `io` on Dispatchers.IO, then `ui` on Dispatchers.Main if the fragment is still added. */
    @JvmStatic
    fun runIO(fragment: Fragment, io: Runnable, ui: Runnable) {
        ownerOf(fragment).lifecycleScope.launch(Dispatchers.IO) {
            try {
                io.run()
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                com.example.plantcare.CrashReporter.log(t)
                return@launch
            }
            if (!fragment.isAdded) return@launch
            withContext(Dispatchers.Main) {
                if (fragment.isAdded) ui.run()
            }
        }
    }

    /** Run `io` producing T on IO, then deliver T to `ui` on Main if the fragment is still added. */
    @JvmStatic
    fun <T> runWithResult(fragment: Fragment, io: Callable<T>, ui: Consumer<T>) {
        ownerOf(fragment).lifecycleScope.launch(Dispatchers.IO) {
            val result: T = try {
                io.call()
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                com.example.plantcare.CrashReporter.log(t)
                return@launch
            }
            if (!fragment.isAdded) return@launch
            withContext(Dispatchers.Main) {
                if (fragment.isAdded) ui.accept(result)
            }
        }
    }
}
