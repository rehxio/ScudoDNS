package es.rehxio.scudo.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Ajustes propios de cada marca que cierran apps en segundo plano, además del ahorro de batería de Android.
 *
 * Ninguna marca permite que una app compruebe ni cambie estos ajustes: solo se puede abrir la pantalla
 * y que el usuario los marque. Por eso cada paso se confirma a mano con "Ya lo he hecho".
 * Para cada paso se prueban varias pantallas (cambian entre versiones del sistema) y, si ninguna existe,
 * se abre la ficha de ScudoDNS, que siempre funciona.
 *
 * Fuentes: dontkillmyapp.com, documentación de Samsung, Huawei y realme.
 */
object OemSettings {

    enum class Brand(val label: String) {
        XIAOMI("Xiaomi"), HUAWEI("Huawei"), HONOR("Honor"), OPPO("OPPO"), REALME("realme"), ONEPLUS("OnePlus"),
        VIVO("vivo"), SAMSUNG("Samsung"), ASUS("ASUS"), MEIZU("Meizu"), LETV("LeEco"), OTHER("")
    }

    /** Un paso propio de la marca. [prefKey] guarda que el usuario lo ha confirmado. */
    class Step(
        val prefKey: String,
        val title: String,
        val hint: String,
        val intents: (Context) -> List<Intent>,
    )

    val brand: Brand by lazy {
        val names = listOf(Build.MANUFACTURER, Build.BRAND).map { it.lowercase() }
        fun any(vararg k: String) = names.any { n -> k.any { n.contains(it) } }
        when {
            any("xiaomi", "redmi", "poco") -> Brand.XIAOMI
            any("honor") -> Brand.HONOR
            any("huawei") -> Brand.HUAWEI
            any("oneplus") -> Brand.ONEPLUS
            any("realme") -> Brand.REALME
            any("oppo") -> Brand.OPPO
            any("vivo", "iqoo") -> Brand.VIVO
            any("samsung") -> Brand.SAMSUNG
            any("asus") -> Brand.ASUS
            any("meizu") -> Brand.MEIZU
            any("letv", "leeco") -> Brand.LETV
            else -> Brand.OTHER
        }
    }

    /**
     * En Xiaomi su ahorro de batería sustituye en la práctica al de Android: se muestra solo el suyo.
     * En el resto, el de Android se muestra siempre (se comprueba solo) y los de la marca, aparte.
     */
    val replacesAndroidBattery get() = brand == Brand.XIAOMI

    private fun component(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))
    private fun appDetails(ctx: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    val steps: List<Step> by lazy {
        when (brand) {
            Brand.XIAOMI -> listOf(
                Step("xiaomiBatteryDone", "Sin ahorro de batería",
                    "Elige \"Sin restricciones\" en el ajuste de batería de ScudoDNS y vuelve.") { ctx ->
                    listOf(
                        Intent("miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY")
                            .putExtra("package_name", ctx.packageName).putExtra("package_label", "ScudoDNS"),
                        appDetails(ctx),
                    )
                },
                Step("autostartDone", "Autoinicio en segundo plano",
                    "Activa ScudoDNS en la lista de autoinicio y vuelve.") { ctx ->
                    listOf(
                        // Lista de autoinicio de HyperOS/MIUI (en HyperOS 3 ya no está en la ficha de la app).
                        component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                        appDetails(ctx),
                    )
                },
            )
            Brand.HUAWEI, Brand.HONOR -> listOf(
                Step("autostartDone", "Inicio de aplicaciones",
                    "Busca ScudoDNS, desactiva \"Gestionar automáticamente\" y deja activados Inicio automático, " +
                        "Inicio secundario y Ejecutar en segundo plano.") { ctx ->
                    listOf("com.hihonor.systemmanager", "com.huawei.systemmanager").flatMap { pkg ->
                        listOf(
                            component(pkg, "$pkg.startupmgr.ui.StartupNormalAppListActivity"),
                            component(pkg, "$pkg.appcontrol.activity.StartupAppControlActivity"),
                            component(pkg, "$pkg.optimize.process.ProtectActivity"),
                        )
                    } + appDetails(ctx)
                },
            )
            Brand.OPPO, Brand.REALME, Brand.ONEPLUS -> listOf(
                Step("oemBackgroundDone", "Actividad en segundo plano",
                    "En la ficha de ScudoDNS entra en \"Uso de la batería\" y activa \"Permitir actividad en segundo plano\" " +
                        "y \"Permitir inicio automático\".") { ctx ->
                    listOf(
                        appDetails(ctx),
                        component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                        component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                        component("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                    )
                },
            )
            Brand.VIVO -> listOf(
                Step("oemBackgroundDone", "Alto consumo en segundo plano",
                    "En Ajustes → Batería → \"Alto consumo en segundo plano\", activa ScudoDNS.") { ctx ->
                    listOf(
                        component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                        Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
                        appDetails(ctx),
                    )
                },
                Step("autostartDone", "Inicio automático",
                    "Activa ScudoDNS en la lista de inicio automático y vuelve.") { ctx ->
                    listOf(
                        component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                        component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                        appDetails(ctx),
                    )
                },
            )
            Brand.SAMSUNG -> listOf(
                Step("oemBackgroundDone", "Apps nunca en suspensión",
                    "Pulsa + y añade ScudoDNS a \"Apps nunca en suspensión\".") { ctx ->
                    listOf(
                        // Acceso directo documentado por Samsung (2 = apps nunca en suspensión).
                        Intent("com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY")
                            .setPackage("com.samsung.android.lool").putExtra("activity_type", 2),
                        appDetails(ctx),
                    )
                },
            )
            Brand.ASUS -> listOf(
                Step("autostartDone", "Inicio automático",
                    "Activa ScudoDNS en el gestor de inicio automático y vuelve.") { ctx ->
                    listOf(
                        component("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
                        component("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity"),
                        appDetails(ctx),
                    )
                },
            )
            Brand.MEIZU -> listOf(
                Step("oemBackgroundDone", "Permisos en segundo plano",
                    "Permite que ScudoDNS funcione en segundo plano y vuelve.") { ctx ->
                    listOf(
                        Intent("com.meizu.safe.security.SHOW_APPSEC").addCategory(Intent.CATEGORY_DEFAULT)
                            .putExtra("packageName", ctx.packageName),
                        appDetails(ctx),
                    )
                },
            )
            Brand.LETV -> listOf(
                Step("autostartDone", "Inicio automático",
                    "Activa ScudoDNS en el gestor de inicio automático y vuelve.") { ctx ->
                    listOf(component("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"), appDetails(ctx))
                },
            )
            Brand.OTHER -> emptyList()
        }
    }

    /** Abre la primera pantalla que exista en este móvil. */
    fun open(ctx: Context, step: Step): Boolean = openFirst(ctx, step.intents(ctx))

    fun openFirst(ctx: Context, intents: List<Intent>): Boolean {
        for (i in intents) {
            if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return true
        }
        return false
    }

    /** Diálogo de Android "¿Permitir que ScudoDNS se ejecute en segundo plano?", solo para esta app. */
    fun androidBattery(ctx: Context) = openFirst(ctx, listOf(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
        appDetails(ctx),
    ))

    /** Lista de VPN de Android: no se puede abrir directamente la ficha de una VPN concreta. */
    fun vpn(ctx: Context) = openFirst(ctx, listOf(Intent(Settings.ACTION_VPN_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS)))
}
