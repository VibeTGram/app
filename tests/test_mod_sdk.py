from mods.sdk import (
    FacadeSpec,
    HostContextFactory,
    MethodSpec,
    ModContext,
    SdkSpec,
    SdkPermissionError,
    TypeSpec,
    generate_luau_sdk,
)


def test_sdk_generation_is_deterministic_and_typed() -> None:
    spec = SdkSpec(
        version="1.0.0",
        types=(TypeSpec("Greeting", (("text", "string"),)),),
        facades=(FacadeSpec("ui", "ui.extend", (MethodSpec("show", ("Greeting",), "boolean"),)),),
    )
    first = generate_luau_sdk(spec)
    second = generate_luau_sdk(spec)
    assert first == second
    assert "export type Greeting" in first.types_luau
    assert "function ui.show" in first.facades_luau
    assert first.source_sha256


def test_context_exposes_only_granted_facade() -> None:
    context = HostContextFactory.create(capabilities={"ui.extend"})
    ui = context.facade("ui", capability="ui.extend")
    assert ui.invoke("show", {"text": "hello"}) is None
    try:
        context.facade("telegram", capability="telegram.messages.read")
    except SdkPermissionError:
        pass
    else:
        raise AssertionError("ungranted facade unexpectedly exposed")

    try:
        ModContext(frozenset({"telegram.messages.read"}), _host_token=object())
    except SdkPermissionError:
        pass
    else:
        raise AssertionError("untrusted code manufactured a context")
