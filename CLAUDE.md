# TATA — Lector de mensajes para baja visión

App Android (Kotlin nativo) que muestra los mensajes entrantes de WhatsApp,
SMS, Telegram, etc. en **letra muy grande y alto contraste** (fondo negro,
remitente amarillo, texto blanco), pensada para una persona mayor que ve poco
y no se maneja con TalkBack ni el zoom de Android.

## Cómo funciona
La única vía viable de leer WhatsApp desde otra app es **escuchar las
notificaciones** del sistema. `MessageNotificationListener`
(`NotificationListenerService`) captura las notificaciones de las apps de
mensajería, extrae remitente + texto y las vuelca en `MessageStore`
(en memoria, compartido con la Activity). Si la notificación trae acción de
respuesta directa, guarda su `RemoteInput` para poder contestar.

## Funciones
- Lista a pantalla completa, fuente **Atkinson Hyperlegible** grande, alto contraste.
- Muestra **toda la conversación** (vía `MessagingStyle`), de antiguo a reciente;
  abre desplazada al mensaje más nuevo. No muestra los mensajes que envía la usuaria.
- Botón **🔊 LEER**: lee el mensaje en voz alta (TTS español, solo al pulsar).
- Botón **🎤 RESPONDER**: dictado por voz → envía respuesta vía `RemoteInput`
  (responde a WhatsApp sin abrirlo). Al responder, la conversación desaparece.
- **Llamadas perdidas** (WhatsApp y teléfono, por `CATEGORY_MISSED_CALL`): botón
  verde **📞 LLAMAR** que devuelve la llamada. Al llamar, el aviso desaparece.
- **Autoborrado**: lo no contestado se elimina solo a las 24 h (`MAX_AGE_MS` en
  `MessageStore`; repaso cada minuto desde `MainActivity`).
- Requiere conceder **acceso a notificaciones** una vez (botón ACTIVAR).

## Estructura
- `MessageNotificationListener.kt` — captura notificaciones.
- `MessageStore.kt` — almacén en memoria (máx. 50, recientes primero).
- `Message.kt` — modelo + `ReplyAction`.
- `MainActivity.kt` — UI, TTS y respuesta por voz.
- `MessageAdapter.kt` + `res/layout/item_message.xml` — celda de la lista.

## Compilar
SDK en `/home/deb/android-sdk` (build-tools 34, platform android-34).
```
./gradlew :app:assembleDebug
```
APK → `app/build/outputs/apk/debug/app-debug.apk`

## Instalar
- Por USB con depuración activada: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- O copiar el APK al móvil y abrirlo (permitir "instalar apps desconocidas").
- Tras abrir: pulsar **ACTIVAR** y marcar "TATA" en la lista de acceso a notificaciones.

## Pendiente / ideas
- minSdk 26, targetSdk 34. Firma: solo debug (suficiente para uso personal).
- Posible: botones de llamada a contactos clave; lectura automática opcional.
