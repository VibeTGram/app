package org.vibetgram.gui.api

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe SPI registry for replaceable GUI entrypoints in VibeTGram.
 * Normative reference: docs/architecture/system-architecture.md section 12 & ADR 0005.
 */
object GuiRegistry {

    private val activeEntryPoint = AtomicReference<GuiEntryPoint>(DefaultGuiEntryPoint())
    private val registeredImplementations = ConcurrentHashMap<String, GuiEntryPoint>()

    init {
        val defaultGui = DefaultGuiEntryPoint()
        registeredImplementations[defaultGui.descriptor.id] = defaultGui
    }

    fun getActiveEntryPoint(): GuiEntryPoint {
        return activeEntryPoint.get()
    }

    fun setActiveEntryPoint(entryPoint: GuiEntryPoint) {
        registeredImplementations[entryPoint.descriptor.id] = entryPoint
        activeEntryPoint.set(entryPoint)
    }

    fun register(entryPoint: GuiEntryPoint) {
        registeredImplementations[entryPoint.descriptor.id] = entryPoint
    }

    fun get(id: String): GuiEntryPoint? {
        return registeredImplementations[id]
    }

    fun listAvailable(): List<GuiDescriptor> {
        return registeredImplementations.values.map { it.descriptor }
    }

    fun resetToDefault() {
        val defaultGui = DefaultGuiEntryPoint()
        registeredImplementations.clear()
        registeredImplementations[defaultGui.descriptor.id] = defaultGui
        activeEntryPoint.set(defaultGui)
    }
}
