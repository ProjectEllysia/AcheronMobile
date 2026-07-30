# Revisión de código — Ellysia Acheron Mobile

> Auditoría de mantenibilidad (DRY / SOLID / LEAN), fragilidad y oportunidades de
> producto sobre el estado del repositorio en la rama `fix/code-review`.
> Alcance de los cambios propuestos: **módulo `app`**. `AcheronCore` se audita en el
> [Anexo A](#anexo-a--acheroncore-auditado-no-modificable-aquí) pero no se toca: es
> compartido con el cliente web y su criptografía tiene tests de interoperabilidad.

---

## 1. Resumen ejecutivo

El proyecto está por encima de la media en documentación interna y tiene un sistema de
marca (`ui/theme/Brand.kt`) coherente y reutilizable. Los problemas no son de estilo sino
estructurales y se concentran en tres focos:

1. **Ciclo de vida de Android.** `MainActivity.onCreate` reconstruye los servicios globales
   en cada rotación de pantalla, dejando conviviendo dos `VaultCryptoService`; el que
   conserva la bóveda descifrada en memoria no es el que `logout()` bloquea.
2. **Sesión.** Un refresh token caducado deja al usuario atrapado en una pantalla con un
   snackbar de error en vez de llevarlo a login; y al abrir la app se fuerza login completo
   aunque el refresh siga siendo válido.
3. **Duplicación estructural.** El conjunto de campos de cada tipo de secreto está
   enumerado a mano en **siete** sitios distintos. Olvidar uno no rompe la compilación: el
   cambio del usuario se descarta en silencio.

A esto se suma la ausencia de las defensas que se esperan de un gestor de contraseñas
(bloqueo de capturas, autobloqueo, protección del portapapeles) y una cobertura de tests
efectivamente nula en el módulo `app`.

| Categoría | Alto impacto | Medio | Bajo | Total |
|---|---:|---:|---:|---:|
| Bugs y fragilidades (B) | 4 | 3 | 4 | 11 |
| Seguridad (S) | 3 | 4 | 0 | 7 |
| DRY / SOLID / LEAN (D) | 1 | 6 | 5 | 12 |
| Funcionalidades ausentes (F) | 4 | 4 | 0 | 8 |
| **Total** | **12** | **17** | **9** | **38** |

Escala de esfuerzo: **S** ≤ medio día · **M** 1–3 días · **L** > 3 días.

**Estado de implementación.** Cada hallazgo abordado lleva una línea `**Estado:**` bajo su
título. Si no aparece, sigue sin implementarse. Valores posibles: *completado y testeado*
(cambio aplicado y verificado en ejecución/tests), *completado y por testear* (cambio
aplicado, pendiente de correr en un dispositivo/emulador o en CI — ver la nota sobre el
entorno de build más abajo), *a medias* (una parte del hallazgo resuelta, el resto
documentado como pendiente), *diferido* (se decidió conscientemente no aplicarlo ahora, con
la razón).

> **Nota sobre verificación:** el entorno donde se implementaron estos cambios no pudo
> ejecutar `./gradlew assembleDebug`, por dos motivos sucesivos y no relacionados con el
> código:
>
> 1. `gradle.properties` fijaba `org.gradle.java.home` a una ruta de JDK 25 inexistente en
>    esa máquina — **corregido**, ahora apunta a `C:/tools/java/sdk/jbr-17` (el JDK 17 que
>    el proyecto requiere, ya presente en `JAVA_HOME`).
> 2. El propio entorno de ejecución de comandos (una shell en sandbox) bloquea los sockets
>    de dominio Unix que el demonio de Gradle necesita internamente para arrancar —
>    reproducido con un `Selector.open()` mínimo sin Gradle de por medio; no es arreglable
>    desde el proyecto.
>
> Se pidió al usuario ejecutar el build en su propia máquina. Al intentarlo, apareció un
> tercer obstáculo, este sí en su entorno: Gradle no pudo resolver el plugin
> `org.gradle.toolchains.foojay-resolver-convention:1.0.0` (versión válida y vigente,
> verificado contra el Gradle Plugin Portal) desde ninguno de los repositorios
> configurados. La causa confirmada es una VPN/proxy corporativo en esa red que bloquea la
> resolución de plugins externos — no un problema de `settings.gradle.kts` ni de los
> cambios de esta revisión. **Decisión:** por indicación del usuario, la verificación en
> ejecución queda pendiente hasta poder correr el build desde otra red; mientras tanto se
> sigue implementando el roadmap y todo lo de esta pasada queda como "completado y por
> testear" salvo que se indique lo contrario.

---

## 2. Matriz de priorización

Se prioriza de izquierda a derecha y de arriba abajo. La celda **Alto × S** es la Fase 1.

| | **Esfuerzo S** | **Esfuerzo M** | **Esfuerzo L** |
|---|---|---|---|
| **Impacto alto** | B1, B2, B3, B4, S1, S3, F1, F2, F6 | S2, F3, F4 | D1 |
| **Impacto medio** | B5, B6, S4, S6, S7, D2, D5 | B7, S5, D3, D4, D8, D12, F7 | D9, F8 |
| **Impacto bajo** | B8, B9, B10, B11, D6, D7, D10, D11 | — | — |

---

## 3. Hallazgos

### 3.1 Bugs y fragilidades

---

#### B1 — Los servicios globales se reconstruyen en cada rotación · `bug` · Alto · S

**Estado:** completado y por testear. `VaultServiceLocator.initialize(Context)` y
`NetworkModule.initialize(...)` son ahora idempotentes (guardan un flag `initialized` y
retornan sin efecto en llamadas posteriores); `MainActivity.onCreate` los invoca así en
vez de construir las instancias a mano, y solo repuebla `username` si sigue vacío para no
pisar un logout hecho entre rotaciones. Pendiente de probar en dispositivo: rotar con la
bóveda abierta y confirmar con un log o breakpoint que `VaultCryptoService` es la misma
instancia antes y después.

**Dónde:** [`MainActivity.kt:50-71`](../app/src/main/java/com/ellysia/acheronmobile/MainActivity.kt),
[`VaultViewModel.kt:34-35`](../app/src/main/java/com/ellysia/acheronmobile/ui/vault/VaultViewModel.kt),
[`NetworkModule.kt:53`](../app/src/main/java/com/ellysia/acheronmobile/data/network/NetworkModule.kt)

`onCreate` ejecuta incondicionalmente:

```kotlin
NetworkModule.initialize(tokenRepository)
VaultServiceLocator.cryptoService  = VaultCryptoService()
VaultServiceLocator.remoteDataSource = VaultRemoteDataSource()
VaultServiceLocator.biometricStore = BiometricMasterPasswordStore(applicationContext)
```

El manifiesto no declara `configChanges`, así que una rotación destruye y recrea la
Activity. Los ViewModels, en cambio, **sobreviven** y capturaron sus dependencias por valor
en el constructor (`private val crypto = VaultServiceLocator.cryptoService`).

**Síntoma para el usuario:** tras rotar la pantalla con la bóveda abierta existen dos
instancias de `VaultCryptoService`. `LoginViewModel.logout()` llama a
`VaultServiceLocator.cryptoService.lock()`, que bloquea **la instancia nueva y vacía**,
mientras la instancia antigua —viva, referenciada por el `VaultViewModel` retenido—
conserva todos los secretos en texto claro en memoria. Cerrar sesión deja de garantizar que
la bóveda se ha purgado. Adicionalmente se fuga un `OkHttpClient` con su pool de conexiones
por cada rotación.

**Corrección:** hacer la inicialización idempotente. Moverla a una subclase de
`Application`, o proteger `NetworkModule.initialize` y el poblado del service locator con
una comprobación de "ya inicializado". Es la corrección táctica; la definitiva es D8.

---

#### B2 — La sesión expirada no lleva a login · `bug` · Alto · S

**Estado:** completado y por testear. `VaultRemoteDataSource.errorMessage()` ahora señala
`SessionEvents.signalEnd(...)` ante **cualquier** 401, distinguiendo `"password_changed"`
de `"expired"` según el cuerpo de error; `LoginViewModel.notifySessionEnded` tiene rama
explícita para `"expired"`. Nota: esto cubre las llamadas de `VaultRemoteDataSource`
(bóveda). `MfaSettingsRepository` tiene su propio manejo de 401 y **no** emite
`SessionEvents` — queda fuera de esta pasada; se retoma con D2/D3 en la Fase 3 al unificar
los tres repositorios. Pendiente de probar: forzar un refresh token inválido y comprobar
que la app navega a login con el mensaje de sesión expirada.

**Dónde:** [`VaultRemoteDataSource.kt:115-136`](../app/src/main/java/com/ellysia/acheronmobile/data/repository/VaultRemoteDataSource.kt),
[`TokenAuthenticator.kt:16-43`](../app/src/main/java/com/ellysia/acheronmobile/data/network/TokenAuthenticator.kt)

Cuando el refresh falla, `TokenAuthenticator` devuelve `null`, OkHttp propaga el 401 y
`errorMessage()` lo traduce a la cadena `"Sesion expirada"`. Pero solo emite el evento
global de fin de sesión en un caso concreto:

```kotlin
if (response.code() == 401 && parsed?.isPasswordChanged() == true) {
    SessionEvents.signalEnd("password_changed")
}
```

**Síntoma para el usuario:** con un refresh token simplemente caducado (el caso común, no el
de contraseña cambiada) aparece un snackbar "Sesion expirada" y la app **se queda donde
está**. Ninguna acción posterior funciona y no hay camino de vuelta al login salvo cerrar
sesión manualmente desde el menú.

**Corrección:** emitir `SessionEvents.signalEnd("expired")` ante cualquier 401 tras el
reintento, y añadir el motivo `"expired"` al `when` de
[`LoginViewModel.notifySessionEnded`](../app/src/main/java/com/ellysia/acheronmobile/ui/login/LoginViewModel.kt)
(ya tiene la rama `else` con el texto correcto).

---

#### B3 — Se fuerza login completo aunque el refresh token siga vigente · `bug` · Alto · S

**Estado:** completado y por testear. `TokenRepository.hasValidSession()` ahora devuelve
`true` si el access token es válido **o** si existe un refresh token (dejando que
`TokenAuthenticator` lo renueve de forma transparente en la primera llamada). Pendiente de
probar: dejar expirar el access token con un refresh válido y confirmar que la app entra
directo a `MASTER_KEY` sin pedir credenciales.

**Dónde:** [`LoginViewModel.kt:32-33`](../app/src/main/java/com/ellysia/acheronmobile/ui/login/LoginViewModel.kt),
[`TokenRepository.kt:68-75`](../app/src/main/java/com/ellysia/acheronmobile/data/repository/TokenRepository.kt),
[`AcheronNavGraph.kt:56`](../app/src/main/java/com/ellysia/acheronmobile/navigation/AcheronNavGraph.kt)

`hasValidSession()` solo comprueba que el **access token** no haya expirado:

```kotlin
fun hasValidSession(): Boolean = getAccessToken() != null && !isAccessTokenExpired()
```

El destino inicial del `NavHost` se decide con eso.

**Síntoma para el usuario:** abrir la app pasada la vida del access token (típicamente una
hora) manda a la pantalla de login con usuario y contraseña —y con MFA si está activo—
aunque el refresh token siga siendo perfectamente válido y `TokenAuthenticator` fuese a
renovarlo de forma transparente en la primera llamada.

**Corrección:** que el destino inicial dependa de la existencia de credenciales renovables
(`getAccessToken() != null || getRefreshToken() != null`), dejando que el authenticator
resuelva la renovación y que B2 gestione el caso en que ya no sea posible.

---

#### B4 — Navegación dentro de la composición · `fragilidad` · Alto · S

**Estado:** completado y por testear. La rama sin `challengeToken` de la ruta `MFA_VERIFY`
envuelve ahora el `navController.navigate(...)` en `LaunchedEffect(Unit) { ... }`.
Pendiente de probar: restaurar el proceso sobre esa ruta (o navegar a ella directamente en
un test de navegación) y confirmar que no hay navegación duplicada ni recomposición en
bucle.

**Dónde:** [`AcheronNavGraph.kt:106-111`](../app/src/main/java/com/ellysia/acheronmobile/navigation/AcheronNavGraph.kt)

```kotlin
composable(Routes.MFA_VERIFY) {
    val challengeToken = pendingChallengeToken
    if (challengeToken == null) {
        navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
    } else { ... }
}
```

`navigate` es un efecto secundario ejecutado durante la composición. Compose no garantiza
cuántas veces se ejecuta un bloque componible, de modo que puede navegarse dos veces o
entrar en un ciclo de recomposición. Es el mismo caso que el resto del fichero **sí**
resuelve correctamente con `LaunchedEffect` (líneas 71-79).

**Síntoma para el usuario:** al restaurar el proceso sobre la ruta de verificación MFA, la
navegación de vuelta a login puede duplicarse o quedar en bucle.

**Corrección:** envolver la rama en `LaunchedEffect(Unit) { ... }` y renderizar entretanto
un contenido vacío o de carga.

---

#### B5 — Pantalla en blanco si el elemento no está en el estado · `bug` · Medio · S

**Estado:** completado y por testear. Las rutas `STORABLE_DETAIL` y `STORABLE_EDIT` ahora
leen con `collectAsStateWithLifecycle()` en vez de `.value`, y cuando el id no aparece en el
estado muestran `MissingStorableContent` (nueva composable privada en `AcheronNavGraph.kt`):
un texto explicativo y un botón "Volver", en vez de nada. Pendiente de probar: navegar al
detalle de un id que ya no existe (p. ej. tras borrarlo desde otro dispositivo y sincronizar)
y confirmar que aparece el aviso en vez de una pantalla en blanco.

**Dónde:** [`AcheronNavGraph.kt:181-224`](../app/src/main/java/com/ellysia/acheronmobile/navigation/AcheronNavGraph.kt)

```kotlin
val storable = vaultViewModel.uiState.value.storables.find { it.id == id }
if (storable != null) { StorableDetailScreen(...) }
```

Dos problemas en dos líneas: `uiState.value` es una lectura **no reactiva** (la composición
no se suscribe al `StateFlow`), y la rama `else` implícita no renderiza absolutamente nada.

**Síntoma para el usuario:** si se llega a la ruta de detalle con la bóveda aún cargando, o
tras un bloqueo, o con un id obsoleto, aparece una pantalla completamente en blanco sin
barra superior ni botón de volver — hay que usar el gesto de retroceso del sistema.

**Corrección:** leer con `collectAsStateWithLifecycle()` y añadir un estado de "elemento no
disponible" con botón de volver.

---

#### B6 — `!!` sobre cuerpos de respuesta y campos opcionales · `fragilidad` · Medio · S

**Estado:** completado y por testear, resuelto junto con D2. El nuevo helper
`apiCall` (`data/network/ApiCall.kt`) trata una respuesta exitosa con cuerpo
nulo como `ApiResult.Error("Respuesta vacía del servidor")` en vez de
`response.body()!!`. En `AuthRepository`, `onTokenResponse` sustituye
`body.challengeToken!!` / `body.accessToken!!` / `body.expiresIn!!` por `?:
return AuthResult.Error(...)` con un mensaje explícito por campo ausente.
Pendiente de probar: no hay forma sencilla de forzar un 200 con cuerpo vacío
sin instrumentar el servidor; se deja como caso de regresión a vigilar más
que a reproducir activamente.

**Dónde:** [`VaultRemoteDataSource.kt:29`](../app/src/main/java/com/ellysia/acheronmobile/data/repository/VaultRemoteDataSource.kt)
(y los otros cinco métodos),
[`AuthRepository.kt:66-76`](../app/src/main/java/com/ellysia/acheronmobile/data/repository/AuthRepository.kt)

Las seis llamadas del data source hacen `Result.Success(response.body()!!)`.
`AuthRepository.handleTokenResponse` hace `body.challengeToken!!`, `body.accessToken!!` y
`body.expiresIn!!` — sobre un `TokenResponse` cuyos campos son **todos nullable por
diseño**, precisamente porque el servidor no los envía todos en cada caso.

**Síntoma para el usuario:** un 204, una respuesta vacía o una combinación de campos no
prevista (por ejemplo `mfaRequired: true` sin `challengeToken`) produce un cierre inesperado
de la app en vez de un mensaje de error.

**Corrección:** sustituir por `?: return Result.Error(...)` con un mensaje de "respuesta
inesperada del servidor". Encaja de forma natural en el helper de D2.

---

#### B7 — Divergencia local/remoto sin rollback · `bug` · Medio · M

**Dónde:** [`VaultViewModel.kt:117-146`](../app/src/main/java/com/ellysia/acheronmobile/ui/vault/VaultViewModel.kt),
[`VaultCryptoService.kt:138-231`](../app/src/main/java/com/ellysia/acheronmobile/data/vault/VaultCryptoService.kt)

`addStorable` y `deleteStorable` mutan la bóveda en memoria **antes** de la llamada de red y
no revierten si esta falla:

```kotlin
val request = crypto.addStorable(kind, title, fields)   // ya añadido en local
pushStorableResult(remote.addStorable(request))         // puede fallar
```

**Síntoma para el usuario:** si la red falla al crear, el elemento aparece en la lista pero
no existe en el servidor; si falla al borrar, desaparece de la lista pero sigue en el
servidor. En ambos casos la mentira se mantiene hasta el siguiente desbloqueo, momento en el
que un dato aparece o desaparece sin explicación.

**Corrección:** revertir la mutación local en las ramas de error (`crypto.removeStorable` /
re-añadir), o invertir el orden confirmando primero contra el servidor. La segunda opción es
más limpia pero requiere que el id de contenido se calcule antes de insertar.

---

#### B8 — `!!` sobre el JSON del core al rotar la clave maestra · `fragilidad` · Bajo · S

**Estado:** completado y por testear. Los tres `!!` se sustituyeron por un
`require(key)` local que lanza `IllegalStateException` con el nombre del
campo ausente ("El JSON exportado del vault no incluye 'X' tras rotar la
clave maestra"), capturable por el `catch` genérico que ya existe en
`VaultViewModel.changeMasterPassword`.

**Dónde:** [`VaultCryptoService.kt:122-131`](../app/src/main/java/com/ellysia/acheronmobile/data/vault/VaultCryptoService.kt)

```kotlin
put("checker",   root["checker"]!!)
put("vaultKey",  root["vaultKey"]!!)
put("algorithm", root["algorithm"]!!)
```

Estas claves las produce `Vault.toJson()` del core, así que hoy están garantizadas. El
problema es *cuándo* fallaría: `v.changePassword()` ya ha mutado la bóveda en memoria, de
modo que un cambio de formato en el core provocaría un cierre inesperado con la clave ya
rotada localmente y no persistida.

**Corrección:** lanzar `IllegalStateException` con mensaje explícito en vez de `!!`, para que
lo recoja el `catch` que ya existe en `VaultViewModel.changeMasterPassword`.

---

#### B9 — Doble fuente de verdad al desbloquear · `LEAN` · Bajo · S

**Estado:** completado y por testear. `unlockFromJson` ahora devuelve el
`VaultState` resultante directamente (en vez de un `Boolean` ignorado), y
`MasterKeyViewModel.doUnlock` usa ese valor de retorno en el `when` en vez de
releer `crypto.state.value` por separado.

**Dónde:** [`MasterKeyViewModel.kt:135-136`](../app/src/main/java/com/ellysia/acheronmobile/ui/vault/MasterKeyViewModel.kt)

`unlockFromJson` devuelve un `Boolean` que se descarta; acto seguido se vuelve a consultar
`crypto.state.value` para deducir el mismo resultado. Dos representaciones del mismo hecho
que pueden desincronizarse.

**Corrección:** que `unlockFromJson` devuelva directamente el `VaultState` resultante, o
eliminar el valor de retorno y quedarse solo con el estado.

---

#### B10 — El formulario arrastra errores de otras pantallas · `bug` · Bajo · S

**Estado:** completado y por testear. `StorableFormScreen` llama a
`LaunchedEffect(Unit) { vaultViewModel.clearError() }` justo al entrar. Pendiente de probar:
provocar un error de sincronización en la lista y luego abrir el formulario de alta,
confirmando que no aparece el error antiguo.

**Dónde:** [`StorableScreens.kt:378`](../app/src/main/java/com/ellysia/acheronmobile/ui/vault/StorableScreens.kt)

`StorableFormScreen` pinta `uiState.errorMessage` del `VaultViewModel` **compartido**, y no
lo limpia al entrar.

**Síntoma para el usuario:** si falló una sincronización, al abrir el formulario de alta
aparece ese error antiguo bajo los campos, como si el formulario estuviese mal rellenado.

**Corrección:** `LaunchedEffect(Unit) { vaultViewModel.clearError() }` al entrar (el método
ya existe).

---

#### B11 — Filtro sin resultados no muestra estado vacío · `bug` · Bajo · S

**Estado:** completado y por testear. Se resolvió a la vez que F1: la condición de vacío
real (`EmptyVault`) sigue mirando `uiState.storables`, pero ahora hay una rama adicional
—`NoResultsFound`— que se muestra cuando `visible.isEmpty()` con la bóveda no vacía (filtro
de categoría o búsqueda sin coincidencias), distinguiendo el mensaje según haya o no texto
de búsqueda. Pendiente de probar: filtrar por una categoría vacía y buscar un texto sin
coincidencias, comprobando el mensaje en ambos casos.

**Dónde:** [`VaultListScreen.kt:171`](../app/src/main/java/com/ellysia/acheronmobile/ui/vault/VaultListScreen.kt)

La condición del estado vacío mira `uiState.storables` (la lista completa), no `visible` (la
filtrada).

**Síntoma para el usuario:** al seleccionar una categoría sin elementos se ve la tira de
estado y los chips, y debajo un hueco vacío sin ningún mensaje.

**Corrección:** añadir un `item { }` de "sin resultados en esta categoría" cuando
`visible.isEmpty()`. Cubre también el caso vacío del buscador (F1).

---

### 3.2 Seguridad

---

#### S1 — Sin `FLAG_SECURE` · `seguridad` · Alto · S

**Estado:** completado y por testear. `MainActivity.onCreate` fija
`window.setFlags(FLAG_SECURE, FLAG_SECURE)` justo después de `super.onCreate`, para toda
la actividad (incluye login y pantallas de marketing, no solo la bóveda — ver la nota de
la Fase 1 más abajo sobre ese trade-off). Pendiente de probar: intentar una captura de
pantalla con la app abierta y comprobar que el sistema la bloquea, y que la miniatura de
apps recientes aparece en negro.

**Dónde:** ausente en todo `app/src/main` (verificado por búsqueda).

No se establece `WindowManager.LayoutParams.FLAG_SECURE` en ninguna ventana.

**Síntoma para el usuario:** con la bóveda abierta se pueden hacer capturas y grabaciones de
pantalla con contraseñas visibles, y la miniatura del selector de apps recientes muestra el
contenido de la bóveda a cualquiera que mire el dispositivo.

**Corrección:** `window.setFlags(FLAG_SECURE, FLAG_SECURE)` en `MainActivity`. Si se quiere
permitir capturas en las pantallas de marketing/login, activarlo y desactivarlo por
destino de navegación.

---

#### S2 — Sin autobloqueo · `seguridad` · Alto · M

**Estado:** completado y por testear, junto con F4 (mismo cambio cubre
ambos). Implementado sin depender de `ProcessLifecycleOwner` (no estaba entre
las dependencias del proyecto): `MainActivity.onStop`/`onResume` —la app
tiene una única Activity, así que su ciclo de vida equivale al del
proceso para este propósito— llaman a un nuevo
[`AutoLockController`](../app/src/main/java/com/ellysia/acheronmobile/data/security/AutoLockController.kt),
que decide si bloquear según el tiempo transcurrido y la preferencia guardada
en
[`AutoLockPreferences`](../app/src/main/java/com/ellysia/acheronmobile/data/security/AutoLockPreferences.kt).
El instante en que la app pasó a segundo plano se persiste en
`SharedPreferences` (no en memoria) para que sobreviva a la recreación de la
Activity en una rotación — si no, rotar mientras está en segundo plano
reiniciaría el contador y se saltaría el bloqueo. Al bloquear, se detectó y
corrigió un problema colateral: la navegación reactiva a "vault bloqueado"
vivía solo dentro de `VaultListScreen`, así que un bloqueo disparado desde el
detalle o el formulario de un storable no habría llevado a ningún sitio; se
centralizó en `AcheronNavGraph` con cuidado de no disparar en el arranque en
frío (`crypto.state` empieza en `Locked` por defecto) ni de pisar la
navegación de un `logout` explícito.

**Dónde:** ausente (no hay ningún observador de ciclo de vida ni temporizador en el proyecto).

La bóveda solo se bloquea si el usuario pulsa explícitamente "Bloquear" o cierra sesión.

**Síntoma para el usuario:** con la app en segundo plano un día entero, los secretos siguen
descifrados en memoria y basta volver a la app para verlos, sin clave maestra ni huella.

**Corrección:** observar `Lifecycle.Event.ON_STOP` del proceso y bloquear tras un tiempo de
gracia configurable. Ver F4 para la cara de producto.

---

#### S3 — `allowBackup` activo con reglas de plantilla sin editar · `seguridad` · Alto · S

**Estado:** completado y por testear — y más profundo de lo previsto en el plan original:
al revisar el manifiesto para aplicar la corrección se confirmó que **ninguno de los dos
ficheros de reglas estaba siquiera referenciado**; `AndroidManifest.xml` no declaraba
`android:fullBackupContent` ni `android:dataExtractionRules`, así que `allowBackup="true"`
corría sin ninguna exclusión, plantilla o no. Se completaron ambos ficheros excluyendo
`acheron_secure_prefs.xml` y `acheron_biometric_prefs.xml` (dominio `sharedpref`) y se
conectaron los dos atributos al `<application>`. Pendiente de probar: en un dispositivo
con Auto Backup (API 31+), forzar un backup (`adb shell bmgr backup`) y una restauración,
y confirmar que las preferencias cifradas no viajan.

**Dónde:** [`AndroidManifest.xml:12`](../app/src/main/AndroidManifest.xml),
[`backup_rules.xml`](../app/src/main/res/xml/backup_rules.xml),
[`data_extraction_rules.xml`](../app/src/main/res/xml/data_extraction_rules.xml)

Ambos ficheros XML siguen siendo la plantilla generada por Android Studio, con todas las
reglas comentadas, mientras `android:allowBackup="true"`.

**Síntoma para el usuario:** los `EncryptedSharedPreferences` de `TokenRepository` y
`BiometricMasterPasswordStore` se copian a la copia de seguridad en la nube, pero la clave
del Android Keystore que los descifra **no sale del dispositivo**. Al restaurar en un
teléfono nuevo, esos ficheros quedan ilegibles: la app arranca con preferencias corruptas y
puede fallar al leer los tokens o el secreto biométrico.

**Corrección:** excluir explícitamente `acheron_secure_prefs` y `acheron_biometric_prefs` en
ambos ficheros de reglas, o `allowBackup="false"` (lo habitual en un gestor de contraseñas).

---

#### S4 — Portapapeles sin protección · `seguridad` · Medio · S

**Estado:** completado y por testear, junto con F5 (mismo cambio cubre ambos). Nueva
utilidad [`util/ClipboardUtils.kt`](../app/src/main/java/com/ellysia/acheronmobile/util/ClipboardUtils.kt):
`copySensitiveText(context, scope, label, text)` usa el `ClipboardManager` de plataforma
(no el `LocalClipboardManager` de Compose, que no expone esta opción) para marcar el
`ClipData` con `ClipDescription.EXTRA_IS_SENSITIVE` en API 33+. Sustituye a los tres
`clipboard.setText(AnnotatedString(...))` de `StorableScreens.kt` y
`AccountSettingsScreen.kt` (PAN/campos del detalle, secreto TOTP, códigos de recuperación).
Pendiente de probar: copiar un secreto en Android 13+ y confirmar que el sistema no muestra
su vista previa.

**Dónde:** [`StorableScreens.kt:212`](../app/src/main/java/com/ellysia/acheronmobile/ui/vault/StorableScreens.kt),
[`AccountSettingsScreen.kt:231,296`](../app/src/main/java/com/ellysia/acheronmobile/ui/account/AccountSettingsScreen.kt)

Se copian contraseñas, IBAN, claves de licencia, el secreto TOTP y los códigos de
recuperación con un `clipboard.setText(AnnotatedString(...))` plano.

**Síntoma para el usuario:** en Android 13+ el sistema muestra una vista previa del
contenido copiado sobre la pantalla, y el secreto permanece en el portapapeles —accesible a
otras apps— indefinidamente.

**Corrección:** marcar el `ClipData` con el flag de contenido sensible
(`ClipDescription.EXTRA_IS_SENSITIVE`) y borrarlo automáticamente pasados 30–60 s (F5).

---

#### S5 — El PAN real es inaccesible desde la UI · `SOLID` + `funcional` · Medio · M

**Dónde:** [`VaultCryptoService.kt:372-417`](../app/src/main/java/com/ellysia/acheronmobile/data/vault/VaultCryptoService.kt)

`detailsOf` aplica `maskPan()` **en la capa de datos**, así que el número completo nunca
llega a la UI. El detalle de tarjeta ofrece un botón de "mostrar" y otro de "copiar", pero
ambos operan sobre el valor ya enmascarado.

**Síntoma para el usuario:** el ojo de "mostrar" revela `****1111`, y copiar la tarjeta
copia `****1111`. El caso de uso principal de guardar una tarjeta —pegarla en un formulario
de pago— es imposible.

Es además una violación de responsabilidad: una decisión de presentación resuelta en el
servicio de criptografía. Se corrige de forma natural junto a D9.

**Corrección:** exponer el valor real y trasladar el enmascarado al componente `DetailField`,
que ya tiene el estado `revealed`.

---

#### S6 — Tráfico en claro y URL de desarrollo en todos los build types · `seguridad` · Medio · S

**Estado:** diferido — a petición explícita del usuario ("de lo único que no
quiero que te preocupes por ahora es de la seguridad https"). No se ha
tocado `usesCleartextTraffic` ni la configuración de URL por build type en
esta pasada. Sigue pendiente para cuando se retome.

**Dónde:** [`AndroidManifest.xml:13`](../app/src/main/AndroidManifest.xml),
[`app/build.gradle.kts:20`](../app/build.gradle.kts)

`android:usesCleartextTraffic="true"` aplica a todos los build types, y
`ELLYSIA_BASE_URL` está fijada a `http://192.168.1.131:5000/` también en `release`. Ambos
puntos están comentados en el código como "solo para desarrollo", pero nada lo impide.

**Corrección:** mover `usesCleartextTraffic` a un `AndroidManifest.xml` del source set
`debug`, y declarar `ELLYSIA_BASE_URL` por build type con HTTPS en release.

---

#### S7 — R8 activo en release sin reglas propias ni build verificado · `fragilidad` · Medio · S

**Estado:** diferido — no accionable desde aquí. Verificarlo exige generar y
ejecutar `assembleRelease` en un dispositivo real; el entorno de esta pasada
no puede compilar el proyecto (ver la nota de verificación al principio del
documento). Ningún cambio de código relacionado con R8/proguard se ha hecho.

**Dónde:** [`app/build.gradle.kts:24-31`](../app/build.gradle.kts),
[`proguard-rules.pro`](../app/proguard-rules.pro)

`isMinifyEnabled = true` y el fichero de reglas contiene únicamente los comentarios de la
plantilla. Retrofit, OkHttp y kotlinx-serialization aportan sus propias reglas de consumo,
y `AcheronCore` construye su JSON con `JsonObject` a mano (sin reflexión de Gson), así que
*probablemente* funcione — pero no hay constancia de que el APK de release se haya ejecutado
nunca.

**Síntoma potencial:** fallo exclusivo de la build de release, invisible en desarrollo.

**Corrección:** generar `assembleRelease`, instalarlo y recorrer el flujo completo
(login → MFA → crear bóveda → alta de cada tipo → sincronizar). Añadir reglas `-keep` solo
si algo se rompe. Coste real: una tarde de verificación, no de código.

---

### 3.3 DRY / SOLID / LEAN

---

#### D1 — Los campos de cada tipo están enumerados en siete sitios · `DRY` + `OCP` · Alto · L

**Dónde:** [`VaultCryptoService.kt:154-414`](../app/src/main/java/com/ellysia/acheronmobile/data/vault/VaultCryptoService.kt),
[`AcheronModels.kt:13-51`](../app/src/main/java/com/ellysia/acheronmobile/data/model/AcheronModels.kt),
[`StorableTypes.kt`](../app/src/main/java/com/ellysia/acheronmobile/ui/vault/StorableTypes.kt)

**Este es el hallazgo estructural principal del informe.** El mismo conocimiento —qué campos
tiene cada tipo de secreto— está escrito a mano en siete lugares:

| # | Sitio | Qué enumera |
|---|---|---|
| 1 | `createStorable` | los argumentos posicionales del constructor del core |
| 2 | `applyField` | los setters, tipo a tipo, campo a campo |
| 3 | `detailsOf` | los getters para la UI |
| 4 | `kindOf` | la correspondencia clase → identificador de tipo |
| 5 | `buildCreateRequest` | 30 llamadas `field("...")` |
| 6 | `StorableCreateRequest` | 30 propiedades nullable |
| 7 | `StorableTypes` | los `FieldSpec` de la UI |

Añadir un octavo tipo de secreto obliga a tocar los siete: es exactamente la violación del
principio abierto/cerrado que el propio comentario de `StorableTypes` dice querer evitar
(*"añadir un tipo nuevo no exige tocar ramas `if` por toda la interfaz"* — cierto para la
UI, falso para el resto del recorrido).

**Síntoma para el usuario:** el fallo es **silencioso**. Si un `FieldSpec` existe en
`StorableTypes` pero falta la rama correspondiente en `applyField`, la función devuelve
`false`, el campo no entra en `changed`, y `updateStorable` devuelve éxito. El usuario edita
el campo, pulsa guardar, no ve ningún error — y el cambio nunca se guardó. Nada en el
compilador ni en los tests lo detecta.

**Corrección:** un registro de metadatos por tipo que sea la única fuente de verdad, con
lector y escritor por campo:

```kotlin
data class FieldAccessor(
    val key: String,
    val get: (VaultObject) -> String?,
    val set: (VaultObject, String) -> Unit,
)
```

`createStorable`, `applyField`, `detailsOf` y `buildCreateRequest` pasan a derivarse del
registro. El DTO de 30 campos se sustituye por un `Map<String, String>` genérico si la API
lo admite, o se genera desde el registro si no. Requiere la red de tests de D12 antes de
empezar.

---

#### D2 — Seis `try/catch` idénticos, con el helper correcto ya escrito al lado · `DRY` · Medio · S

**Estado:** completado y por testear, extendido más allá del alcance
original. En vez de portar el helper de `MfaSettingsRepository` tal cual, se
extrajo a un fichero común nuevo,
[`data/network/ApiCall.kt`](../app/src/main/java/com/ellysia/acheronmobile/data/network/ApiCall.kt)
(`suspend fun <T> apiCall(errorMessage, block): ApiResult<T>`), reutilizado
por los tres repositorios (`VaultRemoteDataSource`, `MfaSettingsRepository` y
`AuthRepository`) en vez de solo dos — ver D3, que se resolvió a la vez.

**Dónde:** [`VaultRemoteDataSource.kt:25-113`](../app/src/main/java/com/ellysia/acheronmobile/data/repository/VaultRemoteDataSource.kt)
frente a [`MfaSettingsRepository.kt:47-67`](../app/src/main/java/com/ellysia/acheronmobile/data/repository/MfaSettingsRepository.kt)

Los seis métodos de `VaultRemoteDataSource` repiten literalmente el mismo bloque de nueve
líneas: `try` → `isSuccessful` → `Success(body()!!)` → `Error(code, errorMessage(response))`
→ `catch IOException` → `catch Exception`. 88 de sus 137 líneas son esa repetición.

Lo relevante es que **la solución ya existe en el repositorio**: `MfaSettingsRepository`
tiene el helper genérico `private suspend fun <T> call(block: suspend () -> Response<T>)`.

**Corrección:** portar ese helper a `VaultRemoteDataSource` conservando su `errorMessage()`
(que es más rico, con parseo de `ApiErrorResponse` y señal de `SessionEvents`). Los seis
métodos quedan en una línea cada uno. Buen punto de entrada para B2 y B6.

---

#### D3 — Tres jerarquías de resultado equivalentes · `DRY` · Medio · M

**Estado:** completado y por testear. Nuevo
[`data/ApiResult.kt`](../app/src/main/java/com/ellysia/acheronmobile/data/ApiResult.kt)
(`Success` / `Error(code, message)` / `NetworkError`), adoptado íntegramente
por `VaultRemoteDataSource` y `MfaSettingsRepository` (sus propios `Result`/
`MfaResult` desaparecen). `AuthRepository.AuthResult` se mantiene aparte a
propósito —tiene estados genuinamente distintos, `MfaRequired` y
`SessionExpired`, que no son un simple éxito/error de API— pero por dentro
también pasa por `apiCall`, así que ya no repite su propio `try/catch`.
Actualizados los tres consumidores (`VaultViewModel`, `MasterKeyViewModel`,
`MfaSettingsViewModel`) a los nuevos tipos.

**Dónde:** `VaultRemoteDataSource.Result`, `MfaSettingsRepository.MfaResult`,
`AuthRepository.AuthResult`

Tres sealed classes con la misma forma (`Success` / `Error` / `NetworkError`), incompatibles
entre sí. Cada consumidor escribe su propio `when` de tres ramas repitiendo los mismos
mensajes de error.

**Corrección:** un único `ApiResult<T>` en `data/`, con `AuthResult` conservando solo su
variante genuinamente distinta (`MfaRequired`) como envoltorio o como caso adicional.

---

#### D4 — Todos los textos de UI son literales en el código · `DRY` + `LEAN` · Medio · M

**Dónde:** [`strings.xml`](../app/src/main/res/values/strings.xml) (contiene únicamente
`app_name`), todos los ViewModels y pantallas.

Se contabilizan 293 literales de cadena de seis o más caracteres en `ui/`, más los mensajes
de error de los ViewModels. `"Sin conexión. Comprueba tu red."` aparece duplicado
palabra por palabra en cinco ficheros; `"Sin conexión"` (sin la segunda frase) en otros
tres — es decir, la misma situación se le comunica al usuario con dos textos distintos según
por dónde entre.

También hay inconsistencias de acentuación que delatan la falta de un punto central:
`"Sesion expirada"` en `VaultRemoteDataSource` frente a `"Sesión expirada. Inicia sesión de
nuevo."` en `MfaSettingsRepository`.

**Corrección:** extraer a `strings.xml`. Se gana revisión centralizada del copy, coherencia
inmediata y la posibilidad de traducir. Es mecánico pero voluminoso.

---

#### D5 — Barras superiores y diálogos duplicados · `DRY` · Medio · S

**Estado:** completado y por testear, parcialmente por diseño. Se subieron
dos componentes a `ui/theme/`: `BrandTopBar.kt` (título + subtítulo opcional +
acciones) y `BrandDialogs.kt` (`LogoutConfirmDialog`). `StorableScreens`'
`BrandTopBar` privado y `AccountSettingsScreen`'s `AccountSettingsHeader`
desaparecen a favor del compartido; el diálogo de logout duplicado en
`VaultListScreen` y `MasterKeyScreen` pasa a ser una única función. `VaultHeader`
(en `VaultListScreen`) se dejó **aparte a propósito**: no tiene flecha de
volver (usa la marca del río) y encadena varios iconos con un divisor, una
forma distinta que no ganaba nada forzándola al mismo componente.

**Dónde:** [`VaultListScreen.kt:211`](../app/src/main/java/com/ellysia/acheronmobile/ui/vault/VaultListScreen.kt),
[`StorableScreens.kt:72`](../app/src/main/java/com/ellysia/acheronmobile/ui/vault/StorableScreens.kt),
[`AccountSettingsScreen.kt:87`](../app/src/main/java/com/ellysia/acheronmobile/ui/account/AccountSettingsScreen.kt),
[`MasterKeyScreen.kt:491`](../app/src/main/java/com/ellysia/acheronmobile/ui/vault/MasterKeyScreen.kt)

Tres barras superiores privadas con la misma estructura (`Row` + `background` +
`statusBarsPadding` + título + acciones) en tres ficheros distintos. Y el diálogo "Cerrar
sesión de Ellysia" está duplicado íntegro —icono, título, cuerpo de dos frases, dos botones— en
`VaultListScreen` y `MasterKeyScreen`.

**Corrección:** subir un `BrandTopBar` y un `ConfirmDialog` a `ui/theme/`, junto al resto del
sistema de marca que ya vive ahí.

---

#### D6 — Ruta de edición muerta y dos caminos para el mismo caso de uso · `LEAN` · Bajo · S

**Estado:** completado y por testear. Se optó por la ruta (recomendación del
informe): `StorableDetailScreen` ahora recibe `onEdit: () -> Unit` en vez de
alternar un estado `editing` local, y `AcheronNavGraph` lo conecta a
`navController.navigate("storable_edit/${storable.id}")`. El estado `editing`
y su `if (editing) { StorableFormScreen(...); return }` se eliminaron de
`StorableScreens.kt`.

**Dónde:** [`AcheronNavGraph.kt:42,210-224`](../app/src/main/java/com/ellysia/acheronmobile/navigation/AcheronNavGraph.kt),
[`StorableScreens.kt:137-145`](../app/src/main/java/com/ellysia/acheronmobile/ui/vault/StorableScreens.kt)

`Routes.STORABLE_EDIT` está declarada y registrada en el `NavHost`, pero **ningún** sitio
navega a ella. La edición real se hace sustituyendo el contenido dentro de la pantalla de
detalle con un `if (editing) { StorableFormScreen(...); return }`.

Ese camino alternativo tiene además una consecuencia visible: como no es una entrada del
back stack, el gesto de retroceso del sistema durante la edición sale del detalle en vez de
volver a él.

**Corrección:** elegir uno. Lo recomendable es usar la ruta —ya está escrita y arregla el
comportamiento del botón atrás— y eliminar el estado `editing` en línea.

---

#### D7 — `OkHttpClient` muerto en `NetworkModule` · `LEAN` · Bajo · S

**Estado:** completado y testeado (verificable por lectura: era una propiedad `private`
sin ningún uso en el fichero ni fuera de él). La propiedad `okHttpClient` se eliminó al
aplicar B1, que ya tocaba `NetworkModule.initialize` para hacerlo idempotente.

**Dónde:** [`NetworkModule.kt:30-51`](../app/src/main/java/com/ellysia/acheronmobile/data/network/NetworkModule.kt)

La propiedad `private val okHttpClient: OkHttpClient by lazy { ... }` (22 líneas) no se
referencia en ninguna parte: `initialize()` construye su propio cliente desde cero. Duplica
además la configuración de timeouts, que es donde alguien la buscaría para cambiarla.

**Corrección:** borrarla.

---

#### D8 — Los ViewModels dependen de un service locator global mutable · `DIP` · Medio · M

**Dónde:** [`VaultServiceLocator.kt`](../app/src/main/java/com/ellysia/acheronmobile/di/VaultServiceLocator.kt),
[`VaultViewModel.kt:34-35`](../app/src/main/java/com/ellysia/acheronmobile/ui/vault/VaultViewModel.kt),
[`MasterKeyViewModel.kt:39-41`](../app/src/main/java/com/ellysia/acheronmobile/ui/vault/MasterKeyViewModel.kt)

```kotlin
object VaultServiceLocator {
    lateinit var cryptoService: VaultCryptoService
    lateinit var remoteDataSource: VaultRemoteDataSource
    lateinit var biometricStore: BiometricMasterPasswordStore
    var username: String = ""
}
```

Cuatro variables globales mutables sin sincronización, leídas desde los ViewModels en el
constructor. Es la causa raíz de B1, y hace que `VaultViewModel` y `MasterKeyViewModel` sean
imposibles de instanciar en un test unitario sin arrancar la Activity — que es por lo que D12
sigue en cero.

Nótese el contraste: `LoginViewModel` y `MfaVerifyViewModel` **sí** reciben sus dependencias
por constructor. El patrón correcto ya está en el proyecto, solo que aplicado a la mitad.

**Corrección:** inyectar por constructor mediante `viewModelFactory { }` (ver D10), dejando
la construcción de los servicios en un contenedor de nivel `Application`.

---

#### D9 — `VaultCryptoService` con cuatro responsabilidades · `SRP` · Medio · L

**Dónde:** [`VaultCryptoService.kt`](../app/src/main/java/com/ellysia/acheronmobile/data/vault/VaultCryptoService.kt) (418 líneas)

En una sola clase conviven: (1) ciclo de vida criptográfico —abrir, crear, exportar, rotar
clave, bloquear—; (2) mapeo al DTO de la API (`buildCreateRequest`, `encryptChangedFields`);
(3) mapeo al modelo de UI (`storablesToUi`, `detailsOf`, `kindOf`); y (4) política de
presentación de secretos (`maskPan`).

Es el fichero donde viven D1 y S5, y donde cualquier cambio de UI obliga a tocar código de
criptografía.

**Corrección:** dividir en `VaultSession` (1), `StorableApiMapper` (2) y `StorableUiMapper`
(3), eliminando (4) según S5. Va junto con D1 en la Fase 4.

---

#### D10 — Cuatro factorías de ViewModel escritas a mano · `DRY` · Bajo · S

**Estado:** completado y por testear. Las cuatro factorías (`MainActivity`
para `LoginViewModel`, y en `AcheronNavGraph` para `MfaVerifyViewModel` y
`MfaSettingsViewModel` — la cuarta contada en el hallazgo original resultó
ser la misma de `MfaVerifyViewModel` revisada dos veces) se sustituyeron por
`viewModelFactory { initializer { ... } }`, sin `@Suppress("UNCHECKED_CAST")`.

**Dónde:** [`MainActivity.kt:38-48`](../app/src/main/java/com/ellysia/acheronmobile/MainActivity.kt),
[`AcheronNavGraph.kt:113-140`](../app/src/main/java/com/ellysia/acheronmobile/navigation/AcheronNavGraph.kt)

Cuatro objetos anónimos `ViewModelProvider.Factory` con su `@Suppress("UNCHECKED_CAST")`,
diez líneas cada uno.

**Corrección:** usar el DSL `viewModelFactory { initializer { ... } }` de
`lifecycle-viewmodel`, ya presente en las dependencias. Cada factoría baja a dos líneas sin
supresiones.

---

#### D11 — Restos de desarrollo en el código de producción · `LEAN` · Bajo · S

**Estado:** a medias. Se eliminaron los comentarios `// ← NUEVO` y `// ← AÑADIR ESTO`. La
segunda parte —decidir sobre `SessionExpired`— se deja **diferida**: wiring pendiente,
documentado ahora con una nota en el propio código (`AuthRepository.kt`). Razón: no hay
forma de saber, solo con este repositorio, qué código o forma de error usa el backend para
un `challengeToken` de MFA caducado; wiring a ciegas arriesgaba conflatarlo con un código
simplemente incorrecto (mensaje erróneo al usuario). Requiere confirmar el contrato con el
backend antes de emitir el estado.

**Dónde:** [`AuthRepository.kt:20`](../app/src/main/java/com/ellysia/acheronmobile/data/repository/AuthRepository.kt),
[`LoginViewModel.kt:78`](../app/src/main/java/com/ellysia/acheronmobile/ui/login/LoginViewModel.kt)

```kotlin
data object SessionExpired : AuthResult()   // ← NUEVO
...
AuthRepository.AuthResult.SessionExpired -> {   // ← AÑADIR ESTO
```

`SessionExpired` se consume en dos `when` pero **`AuthRepository` no lo emite nunca**: es una
rama inalcanzable. Las marcas `← NUEVO` / `← AÑADIR ESTO` son notas de una sesión de trabajo
que quedaron dentro.

**Corrección:** eliminar los comentarios; y decidir sobre `SessionExpired` — es el estado
natural para B2, así que lo razonable es emitirlo en lugar de borrarlo.

---

#### D12 — Sin cobertura de tests en el módulo `app` · `fragilidad` · Medio · M

**Estado:** completado y por testear (en el sentido de "por ejecutar": no se
han podido correr en este entorno, ver la nota de verificación al principio
del documento — pero al ser tests JUnit4 puros, sin Robolectric ni
dependencias de Android framework, deberían ejecutarse sin emulador en
cuanto se pueda compilar). Tres ficheros nuevos, exactamente los tres
apuntados por la corrección original:
- [`VaultCryptoServiceTest.kt`](../app/src/test/java/com/ellysia/acheronmobile/data/vault/VaultCryptoServiceTest.kt) —
  recorre los siete tipos de `StorableTypes.all` creando y actualizando un
  storable con todos sus campos, y falla explícitamente si alguno se pierde
  en `createStorable`/`applyField`/`detailsOf` (el escenario exacto de D1).
- [`StorableTypesTest.kt`](../app/src/test/java/com/ellysia/acheronmobile/ui/vault/StorableTypesTest.kt) —
  `StorableTypes.of()` para tipos conocidos y el fallback ante un kind
  desconocido.
- [`ApiErrorResponseTest.kt`](../app/src/test/java/com/ellysia/acheronmobile/data/model/ApiErrorResponseTest.kt) —
  `isPasswordChanged()` y `displayMessage()` (incluida la lista de permisos
  faltantes).

Los ViewModels (`VaultViewModel`, `MasterKeyViewModel`) siguen sin tests:
dependen de `VaultServiceLocator` (D8, Fase 4) y no son instanciables sin
arrancar la Activity.

**Dónde:** [`ExampleUnitTest.kt`](../app/src/test/java/com/ellysia/acheronmobile/ExampleUnitTest.kt)

El único test del módulo `app` es la plantilla `assertEquals(4, 2 + 2)`. `AcheronCore`, en
cambio, tiene una suite razonable (`VaultTest`, `AccountTest`, `CreditCardTest`, tests de
las dos estrategias de derivación, generador de vectores de interop).

El punto crítico es la coincidencia: la zona sin tests es exactamente donde D1 produce
fallos silenciosos. Un test que recorriese los siete tipos verificando que cada `FieldSpec`
tiene su rama en `applyField` y su clave en `detailsOf` cabe en 30 líneas y cierra la clase
entera de bug.

**Corrección:** empezar por ahí, después `StorableTypes.of()`, el mapeo de errores de
`ApiErrorResponse` y `validateNewPassword`. Los ViewModels quedan cubiertos cuando D8 los
haga instanciables.

---

### 3.4 Funcionalidades ausentes (propuestas)

| id | Propuesta | Por qué importa | Sobre qué se construye | Impacto | Esfuerzo | Estado |
|---|---|---|---|---|---|---|
| **F1** | **Buscador** en la lista, filtrando por título y por el campo de subtítulo | Hoy solo hay filtro por categoría; a partir de 40–50 elementos encontrar algo exige recorrer la lista a mano. Es la interacción más frecuente en un gestor de contraseñas | `VaultListScreen` ya calcula `visible`: es un `filter` adicional sobre la misma variable | Alto | S | Completado y por testear |
| **F2** | **Generador de contraseñas** en el formulario de alta (longitud, clases de caracteres, botón de regenerar) | Es la función que más se espera de un gestor. Sin ella el usuario sigue inventando —y reutilizando— contraseñas, que es justo lo que la app existe para evitar | `Brand.kt` ya tiene `rememberStrength` y `PasswordStrengthMeter` para valorar el resultado | Alto | S | Completado y por testear |
| **F3** | **Refresco de la bóveda desde el servidor** (deslizar para actualizar) | La sincronización es hoy solo de subida. Los cambios hechos en el cliente web no aparecen hasta cerrar y reabrir la bóveda, y el usuario no tiene forma de saberlo | `remote.fetchVault()` y `crypto.unlockFromJson` ya existen; hace falta conservar la clave maestra en sesión o volver a pedirla | Alto | M | Diferido — ver nota |
| **F4** | **Autobloqueo configurable** (inmediato / 1 / 5 / 15 min) en Cuenta y ajustes | Cara de producto de S2. Sin control visible, el usuario no puede razonar sobre cuánto tiempo están expuestos sus secretos | La sección de ajustes ya está preparada para "paneles hermanos" (comentario en `AccountSettingsScreen`) | Alto | M | Completado y por testear |
| **F5** | **Borrado automático del portapapeles** a los 30–60 s, avisando en el snackbar | Cara de producto de S4. Convierte una mitigación invisible en una garantía que el usuario ve | El snackbar de copiado ya existe | Medio | S | Completado y por testear |
| **F6** | **Gestión de la huella desde Ajustes** (activar / desactivar / re-enrolar) | Hoy la huella solo se ofrece en el diálogo posterior a un desbloqueo manual. Si el usuario pulsa "Ahora no" una vez, **no hay ninguna forma de activarla** salvo cerrar sesión y volver a entrar | `BiometricMasterPasswordStore` ya soporta enrolar, consultar y borrar; solo falta la pantalla | Alto | S | Completado y por testear |
| **F7** | **Favoritos y ordenación** por fecha de actualización o alfabética | La lista se ordena hoy por el id interno del core (`compareTo` de `VaultObject`), un criterio sin significado para quien la mira | Requiere metadatos por elemento, locales o en servidor | Medio | M | — (Fase 4) |
| **F8** | **Salud de la bóveda**: contraseñas débiles, repetidas, tarjetas caducadas | Diferencia un almacén pasivo de una herramienta que mejora activamente la seguridad del usuario | `rememberStrength` da la valoración; los duplicados son un `groupBy` sobre los detalles descifrados | Medio | L | — (Fase 4) |

> **Nota sobre el diferimiento de F3.** Al diseñarlo se descubrió una restricción
> arquitectónica que no era evidente en la auditoría original: la única vía para
> reconstruir storables desde JSON es `VaultFactory.fromJson` (AcheronCore), que
> **deriva la clave desde la contraseña** como parte del mismo método — no existe
> una API de nivel más bajo que reparse un JSON reutilizando la
> `VaultEncryptingStrategy` ya abierta en memoria sin la contraseña. Añadir esa
> API implicaría modificar `AcheronCore`, fuera de alcance (es compartido con el
> cliente web). Hacerlo sin tocar el core habría significado pedir la contraseña
> maestra de nuevo en cada "deslizar para actualizar", que ya no es la
> funcionalidad descrita. Queda diferido hasta decidir cuál de las dos vías se
> prefiere: extender `AcheronCore` (coordinando con el cliente web) o rediseñar
> F3 como un desbloqueo asistido en vez de un refresco transparente.

---

## 4. Roadmap

### Fase 1 — Estabilidad y seguridad de base

`B1` `B2` `B3` `B4` `S1` `S3` `D7` `D11` — esfuerzo total estimado: **2–3 días**

Cambios acotados, sin refactor, todos de alto impacto. Es lo que hay que hacer antes de
tocar cualquier otra cosa.

**Estado de la fase (esta pasada):** los ocho hallazgos tienen cambio aplicado.
`B1` `B2` `B3` `B4` `S1` `S3` — completado y por testear; `D7` — completado y testeado (el
código muerto se confirma por lectura); `D11` — a medias (limpieza hecha,
`AuthResult.SessionExpired` diferido a falta de conocer el contrato del backend). Ninguno
se ha podido ejecutar en un build real: esta máquina no tiene JDK 17+ instalado. Antes de
dar la fase por cerrada hace falta `./gradlew assembleDebug` y una pasada manual por el
criterio de aceptación de abajo.

Un matiz de S1 que vale la pena anotar aquí: `FLAG_SECURE` se fijó para toda la actividad,
incluida la pantalla de login. Si en algún momento se quiere permitir capturas ahí (p. ej.
para soporte técnico o tutoriales), habría que activarlo/desactivarlo por destino de
navegación en vez de una sola vez en `onCreate` — se dejó así porque es lo más seguro por
defecto y el coste de refinarlo es bajo cuando haga falta.

**Criterio de aceptación**
- Rotar la pantalla con la bóveda abierta no crea una segunda instancia de
  `VaultCryptoService`; tras cerrar sesión no queda ninguna referencia viva con secretos
  descifrados.
- Un refresh token caducado lleva a la pantalla de login con el mensaje correspondiente.
- Abrir la app con el access token expirado pero refresh válido entra directamente a la
  bóveda, sin pedir credenciales.
- La app no aparece en capturas de pantalla ni en la miniatura de recientes.
- La copia de seguridad excluye `acheron_secure_prefs` y `acheron_biometric_prefs`.

### Fase 2 — Quick wins de producto

`F1` `F2` `F6` `F5` `S4` `B10` `B11` `B5` — esfuerzo total estimado: **3–4 días**

Todo funcionalidad muy visible construida sobre helpers que ya existen. Es la fase con mejor
relación impacto/esfuerzo de todo el plan.

**Estado de la fase (esta pasada):** los ocho hallazgos tienen cambio aplicado — todos
completado y por testear (misma limitación de entorno que la Fase 1: sin poder ejecutar
`./gradlew assembleDebug` en esta máquina). Dos hallazgos se resolvieron juntos con el mismo
cambio: F1 y B11 en `VaultListScreen.kt` (el buscador y su estado vacío comparten la misma
lista `visible`); S4 y F5 con la misma utilidad nueva, `util/ClipboardUtils.kt`. F6 fue el
más grande de la fase: además de la pantalla, se añadió `VaultCryptoService.verifyMasterPassword()`
(delegando en `VaultEncryptingStrategy.matchesPassword`, ya existente en el core) para poder
reautenticar sin mutar el vault antes de guardar la clave maestra tras la huella.

**Criterio de aceptación**
- Buscar por texto filtra la lista en vivo y muestra estado vacío cuando no hay resultados.
- El formulario de alta ofrece generar una contraseña, con su medidor de fuerza.
- La huella se puede activar y desactivar desde Cuenta y ajustes en cualquier momento.
- Copiar un secreto avisa del borrado automático y el portapapeles queda limpio al expirar.
- Entrar en el formulario nunca muestra un error heredado de otra pantalla.

### Fase 3 — Consolidación técnica

`D2` `D3` `D5` `D6` `D10` `B6` `B8` `B9` `D12` `S6` `S7` `F3` `F4` `S2` — **1–2 semanas**

Se paga la deuda que no exige rediseño. `D2` primero: al centralizar el manejo de respuestas
arrastra consigo `B6` y facilita `B2`. `D12` al final de la fase, para que la red de tests
esté puesta antes de la Fase 4.

**Estado de la fase (esta pasada):** `D2` `D3` `D5` `D6` `D10` `B6` `B8` `B9`
`D12` `S2` `F4` — completado y por testear (`D11` seguía a medias desde la
Fase 1, sin cambios aquí). `S6` diferido a petición explícita del usuario.
`S7` diferido — exige un build real que este entorno no puede ejecutar (ver
la nota de verificación al principio del documento). `F3` diferido: al
diseñarlo apareció una restricción arquitectónica no evidente en la
auditoría original (ver la nota bajo la tabla de F1–F8) — implementarlo bien
exige o tocar `AcheronCore` (fuera de alcance) o pedir la contraseña de
nuevo en cada refresco, que ya no es "deslizar para actualizar".

**Criterio de aceptación**
- `VaultRemoteDataSource` no repite manejo de errores; ningún `!!` sobre cuerpos de respuesta.
- Un único tipo de resultado compartido por los tres repositorios.
- Suite de tests que cubra el mapeo de tipos de storable, `StorableTypes.of()` y el parseo
  de errores de API.
- APK de release generado, instalado y verificado de extremo a extremo.
- La bóveda se bloquea sola según el tiempo configurado por el usuario.

### Fase 4 — Refactor estructural

`D1` `D9` `S5` `D8` — y después `B7` `F7` `F8` — **2–3 semanas**

Los cuatro se abordan juntos porque son el mismo problema visto desde ángulos distintos:
extraer un registro de metadatos de campo como única fuente de verdad, sacar el mapeo
DTO/UI de `VaultCryptoService`, quitar el enmascarado de la capa de datos y sustituir el
service locator por inyección por constructor.

**Requisito previo:** la suite de tests de `D12` debe estar en su sitio. Sin ella, un
refactor de esta superficie sobre un mapeo que ya falla en silencio es una mala idea.

**Criterio de aceptación**
- Añadir un tipo de secreto nuevo requiere tocar un único fichero del módulo `app`.
- Un campo declarado sin accesor falla en tiempo de compilación o de test, nunca en silencio.
- El número completo de la tarjeta se puede revelar y copiar desde el detalle.
- `VaultViewModel` y `MasterKeyViewModel` se instancian en un test sin arrancar la Activity.

---

## Anexo A — AcheronCore (auditado, no modificable aquí)

Observaciones sobre el motor Java compartido con el cliente web. **Ninguna es accionable
desde este repositorio sin coordinación previa**: `AcheronCore` alimenta también al cliente
web y sus tests de interoperabilidad criptográfica (`VectorGenerator`, `Tests`) fijan el
formato en ambos lados.

- **`VaultObject.equals` / `hashCode` desreferencian un `id` que puede ser `null`.**
  [`VaultObject.java:170-179`](../AcheronCore/src/main/java/com/ellysia/acheron/vault/storables/VaultObject.java).
  Entre la construcción con `needsAutoId = true` y la llamada a `Vault.add`, `id` es `null`;
  meter el objeto en un `Set` o `Map` en esa ventana lanza `NullPointerException`.
  Hoy no ocurre porque el cliente móvil siempre añade inmediatamente, pero el contrato es
  frágil.

- **`VaultFactory.getMockVault` embarca datos personales reales en el artefacto de
  producción.** [`VaultFactory.java:85-194`](../AcheronCore/src/main/java/com/ellysia/acheron/vault/VaultFactory.java).
  Nombre y apellidos reales, un IBAN, dos números de tarjeta con CVV, una contraseña de
  Wi-Fi y una clave de licencia, como constantes en una clase de `src/main`. Aunque el
  método no se invoque desde el móvil, las cadenas viajan en el APK. Su sitio es
  `src/test`.

- **`VaultFactory.fromJson` enumera las siete categorías a mano** con siete bloques
  `if (root.has("..."))`. Es el mismo problema de OCP que D1, en el extremo opuesto del
  sistema: un tipo nuevo obliga a tocar el core, el móvil y el cliente web.

- **`Vault.toString()` puede lanzar `RuntimeException`**
  ([`Vault.java:411-421`](../AcheronCore/src/main/java/com/ellysia/acheron/vault/Vault.java)),
  rompiendo el contrato de `Object.toString`. Relevante porque `MasterKeyViewModel` pasa el
  resultado de `toString()` a `unlockFromJson`, aunque en ese caso se trate del `JsonObject`
  de Kotlin y no de esta clase.
