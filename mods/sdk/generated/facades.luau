-- Generated VibeTGram Mod SDK facades 1.0.0

local ipc = {}
function ipc.call(arg1: string, arg2: IpcValue): IpcValue
    return __host_invoke("ipc", "call", {arg1, arg2})
end

local settings = {}
function settings.get(arg1: string): string
    return __host_invoke("settings", "get", {arg1})
end

local ui = {}
function ui.register(arg1: UiNode): boolean
    return __host_invoke("ui", "register", {arg1})
end

return {ipc = ipc, settings = settings, ui = ui}
