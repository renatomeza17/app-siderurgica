from pathlib import Path
import re

root = Path.cwd()
base = 'app/src/main/java/pe/edu/unmsm/siderurgica/'
out = root / 'outputs' / 'exposicion_sprints_1_2_3.md'
out.parent.mkdir(exist_ok=True)
blocks = []
parts = ['# Código para exposición: Sprints 1, 2 y 3\n',
'''Alcance: HU01–HU09, identificadas en PRODUCT_BACKLOG_FINAL.xlsx, hoja «Backlog actualizado», filas 4–12. El backlog se usa como referencia de requisitos; sus estados y su matriz no prueban implementación.

Este repositorio contiene Android/Kotlin/Compose con MVVM. La equivalencia para exponer es **pantalla → ViewModel (coordinación) → repositorio/servicio → DTO/API REST o entidad/DAO Room → SQLite**. Firebase Authentication y Storage son servicios externos. No contiene los controladores, servicios, entidades ni SQL del backend ERP: los flujos remotos terminan aquí en el contrato Retrofit. No se inventa código del servidor.

Los bloques siguientes son copias literales de líneas contiguas del repositorio, sin pseudocódigo ni puntos suspensivos añadidos. Los rangos figuran fuera del código para facilitar copiarlo. Son fragmentos para un guion, no un único archivo compilable; los imports y dependencias permanecen en sus archivos originales. Las piezas compartidas se incluyen al final y se referencian en cada funcionalidad.
''']

def block(label, file, start=1, end=None):
    path = root / (base + file if file.endswith('.kt') else file)
    lines = path.read_text(encoding='utf-8-sig').splitlines()
    end = len(lines) if end is None else end
    assert 1 <= start <= end <= len(lines), (file, start, end, len(lines))
    code = '\n'.join(lines[start-1:end])
    relative = path.relative_to(root).as_posix()
    parts.append(f'### {label}\n\nOrigen: `{relative}`, líneas {start}–{end}.\n\n```{ "kotlin" if file.endswith(".kt") else "text"}\n{code}\n```\n')
    blocks.append((relative,start,end,code))

def hu(n,title,flow,speech,note):
    sprint = (n-1)//3+1
    parts.append(f'## Sprint {sprint} · HU{n:02d} — {title}\n\n**Flujo:** {flow}\n\n**Guion sugerido:** «{speech}»\n\n**Alcance comprobado:** {note}\n')

hu(1,'Arquitectura MVVM y Material Design 3','MainActivity/SiderurgicaScreen → ErpViewModel → ErpRepository → LocalStore/ErpApi','La pantalla observa un StateFlow del ViewModel. Application proporciona el repositorio y la base Room; los datos remotos se consumen con Retrofit.','Es una historia transversal, sin operación de negocio propia. La provisión de dependencias es manual mediante Application. Ver anexo de estado, corrutinas, Retrofit y Room.')
block('Entrada, observación de estado y navegación','MainActivity.kt')
block('Tema Material 3','ui/Theme.kt')
block('Provisión de dependencias y migración SQLite','SiderurgicaApp.kt')

hu(2,'Registro, inicio, recuperación, cierre y biometría','LoginScreen → ErpViewModel → FirebaseAuth + ErpRepository.login → ErpApi.login → SessionVault','El usuario inicia con correo y contraseña; el prefijo del correo se usa como usuario ERP. Se verifica el ERP y Firebase, y el token ERP se guarda cifrado.','El registro Firebase exige primero una cuenta ERP existente. Firebase no crea usuarios ERP. La recuperación modifica el acceso Firebase; no hay endpoint de recuperación ERP. La biometría y su preferencia están en MainActivity (HU01) y MoreScreen. Los permisos del servidor no pueden verificarse desde este repositorio.')
block('Formulario de acceso','ui/HomeAndLogin.kt',29,85)
block('Preferencia biométrica y cierre','ui/HomeAndLogin.kt',119)
block('Autenticación y cierre en ViewModel','ErpViewModel.kt',68,174)
block('Sesión cifrada y login ERP','data/ErpRepository.kt',24,88)
block('DTO de autenticación','data/ErpApi.kt',8,10)
block('Endpoint de autenticación','data/ErpApi.kt',44,45)

hu(3,'Trabajo offline y sincronización','loadCache/guardar borrador → repositorio.fetch/saveDraft → LocalDao → Room; CacheSyncWorker → repositorio → API → caché','Las consultas remotas se guardan por usuario y tipo. Si falla la red, se recupera la caché. WorkManager refresca lecturas y los borradores se envían con confirmación.','No hay sincronización automática de escrituras ni garantía de idempotencia. No existen tablas independientes de usuarios o productos: hay caché JSON y sesión cifrada. El worker puede completar usando caché; Result.success no demuestra que hubo actualización remota.')
block('Programación periódica y estado','ErpViewModel.kt',21,44)
block('Carga local y refresco por roles','ErpViewModel.kt',175,273)
block('Consulta de caché y envío manual de borradores','ErpViewModel.kt',275,307)
block('Pantalla de borradores','ui/SalesScreen.kt',206)
block('Consulta remota y fallback local','data/ErpRepository.kt',89,108)
block('Persistencia de borradores y worker','data/ErpRepository.kt',151)
block('Entidades, consultas y base Room','data/LocalStore.kt')

hu(4,'Registro y consulta de cotizaciones','SalesScreen/CommercialForm → saveQuote/approveQuote/convertQuote/rejectQuote → ErpRepository → ErpApi → ERP; consultas → caché Room','El formulario prepara una cotización de un producto y muestra importes estimados. El ViewModel valida y el repositorio envía el DTO; la respuesta y los listados se obtienen del ERP.','La interfaz actual muestra aprobar y, después, convertir en pedido como acciones separadas. El README describe conversión automática al aprobar: no se puede resolver esa diferencia sin el backend. Ver anexo de validación, permisos y caché.')
block('Listado, filtros y acciones comerciales compartidas HU04/HU05','ui/SalesScreen.kt',23,104)
block('Formulario compartido HU04/HU05, cálculo y envío','ui/SalesScreen.kt',134,205)
block('Alta de cotización','ErpViewModel.kt',282,286)
block('Aprobación, conversión y rechazo','ErpViewModel.kt',308,318)
block('Consulta, alta y estados en repositorio','data/ErpRepository.kt',90,90)
block('Escrituras comerciales compartidas HU04/HU05','data/ErpRepository.kt',128,150)
block('Modelos de cotización','data/ErpApi.kt',12,25)
block('Endpoints de cotizaciones','data/ErpApi.kt',46,49)

hu(5,'Pedidos y ventas','SalesScreen/CommercialForm → createOrder/orderState/sale → ErpRepository → ErpApi → ERP → refresco de listados/caché','El pedido se registra, se aprueba, rechaza o anula, y una acción sobre el pedido aprobado genera la venta. Los cambios llegan al ERP mediante Retrofit.','Usa las pantallas y métodos compartidos de HU04. El descuento/reserva de stock y las transacciones son del backend no incluido. orderState refresca inventario; sale solo refresca pedidos y ventas. El README afirma venta automática al aprobar, pero la UI actual ofrece generar venta después. No hay reintentos automáticos seguros para POST.')
block('Alta y validación del pedido','ErpViewModel.kt',287,296)
block('Cambios de estado y venta','ErpViewModel.kt',308,318)
block('Lecturas de pedidos y ventas','data/ErpRepository.kt',91,92)
block('Modelos de pedido, venta y estado','data/ErpApi.kt',26,42)
block('Endpoints de pedidos y ventas','data/ErpApi.kt',50,54)

hu(6,'Generación y compartición de PDF','SaleCard → SalePdf.create/share/upload → archivo local / FileProvider / Firebase Storage','A partir del modelo Sale se dibuja un PDF con cliente, producto e importes. Se puede abrir, compartir o subir al espacio Firebase del usuario.','No es factura o boleta tributaria; el propio PDF lo indica. No pasa por un método ViewModel: la pantalla llama al servicio SalePdf con una corrutina. El modelo Sale está en HU05; consultar ventas pasa por repositorio/API/caché.')
block('Acciones de PDF desde la pantalla','ui/SalesScreen.kt',105,133)
block('Servicio de PDF, compartir y subir','SalePdf.kt')
block('Proveedor de archivos Android','app/src/main/AndroidManifest.xml')
block('Rutas locales compartibles','app/src/main/res/xml/file_paths.xml')
block('Reglas Storage disponibles','firebase.storage.rules')

hu(7,'Inventario, movimientos y alertas','InventoryScreen/MovementDialog → saveMovement/sendMovement → repo.changeStock → ErpApi; worker → StockNotifications; FCM → StockMessagingService','El inventario distingue normal, bajo y agotado. Se registran entradas, salidas y ajustes mediante el ERP, y se guardan solicitudes locales para enviarlas después.','El ajuste usa PUT tras leer el producto; entrada/salida usan PATCH. Las alertas periódicas son notificaciones locales. El receptor FCM existe, pero falta registro del token en el ERP y emisor del servidor. No se conserva localmente un historial de movimientos online; el motivo no se envía en StockBody.')
block('Consulta, indicadores y formulario de movimientos','ui/InventoryAndPurchases.kt',20,89)
block('Guardar movimiento online u offline','ErpViewModel.kt',351,362)
block('Enviar movimiento local','ErpViewModel.kt',398,402)
block('Consulta y mutación de inventario','data/ErpRepository.kt',93,93)
block('Servicio de stock','data/ErpRepository.kt',118,126)
block('DTO inventario','data/ErpApi.kt',40,40)
block('Endpoints stock','data/ErpApi.kt',55,58)
block('Modelos de mutación','data/ErpApi.kt',70,72)
block('Notificaciones locales y receptor FCM','domain/StockNotifications.kt')
block('Modelo/DAO de movimientos y operaciones locales','data/Operations.kt')

hu(8,'Proveedores, órdenes y recepción de compra','SupplierScreen/PurchaseScreen → saveSupplier/savePurchase/receivePurchase → repositorio → API ERP; borradores → OperationsDao → SQLite','Se valida RUC y duplicados, se prepara la orden con presupuesto y se registra su recepción. El ERP devuelve compra e inventario; el ViewModel refresca ambos.','La cola local requiere envío manual. El presupuesto ingresado se valida en el cliente; la validación transaccional del presupuesto ERP no puede verificarse. La recepción comprueba importe y duplicados antes de enviar, pero la garantía ante concurrencia corresponde al servidor. Entidades/DAO locales en HU07 y migración en HU01.')
block('Pantallas de proveedores, compras y recepción','ui/InventoryAndPurchases.kt',90)
block('Registrar proveedor y orden','ErpViewModel.kt',321,350)
block('Enviar borradores de proveedores y compras','ErpViewModel.kt',384,397)
block('Estados de compra y recepción','ErpViewModel.kt',403,414)
block('Repositorio de abastecimiento','data/ErpRepository.kt',110,117)
block('Endpoints abastecimiento','data/ErpApi.kt',59,66)
block('Modelos abastecimiento','data/ErpApi.kt',73,80)

hu(9,'Captura, OCR y lectura de códigos','DocumentScreen → DocumentScanner.scan → ML Kit/InvoiceParser → revisión → saveDocument/saveCode → OperationsDao → SQLite; uploadDocument → Firebase Storage → markUploaded','La cámara o galería entrega una imagen. ML Kit reconoce texto y códigos; el parser propone campos, el usuario los corrige y el ViewModel guarda el documento localmente. La subida es una acción separada.','El OCR se procesa en el dispositivo y el JPEG se comprime al 80 %. No existe endpoint ERP de documentos. Las reglas incluidas solo permiten usuarios/{uid}/ventas/{fileName}; no autorizan usuarios/{uid}/documentos/*.jpg. Con esas reglas, la subida de imágenes no queda habilitada. Entidad y DAO están en HU07; FileProvider en HU06.')
block('Captura, revisión y guardado desde UI','ui/Documents.kt')
block('Servicio OCR, códigos y compresión','data/DocumentScanner.kt')
block('Modelo extraído y parser','domain/BusinessRules.kt',27)
block('Guardado local y subida a Storage','ErpViewModel.kt',363,383)

parts.append('## Anexo — piezas compartidas del flujo\n\nEstas piezas completan la coordinación, validación, conexión y persistencia utilizadas por las historias anteriores. Room/SQLite está reproducido en HU03; OperationsDao en HU07; migración SQL en HU01. Los DTO Retrofit no son entidades de la base ERP.\n')
block('Estado, dependencias, corrutinas y tratamiento de errores','ErpViewModel.kt',21,67)
block('Carga compartida local/remota','ErpViewModel.kt',175,273)
block('Funciones auxiliares del ViewModel','ErpViewModel.kt',319,320)
block('Funciones auxiliares de documentos y montos','ErpViewModel.kt',414,416)
block('Reglas de dominio, RUC, correo, descuentos y roles','domain/BusinessRules.kt',6,25)
block('Configuración Retrofit, JWT y reintentos','data/ErpRepository.kt',59,72)
block('Caché genérica por usuario','data/ErpRepository.kt',89,108)
block('Componentes usados por las pantallas','ui/Components.kt')

parts.append('## Verificación de extracción\n\nSe verificó que cada bloque coincide con su rango del archivo fuente. No se modificó el código de la aplicación ni se ejecutaron pruebas Android: este entregable documenta el código presente y no certifica el funcionamiento del backend o de Firebase.\n')
text = '\n'.join(parts)
for file,start,end,code in blocks:
    original = '\n'.join((root/file).read_text(encoding='utf-8-sig').splitlines()[start-1:end])
    assert code == original
    assert code in text
out.write_text(text,encoding='utf-8')
print(f'{out}\n{len(blocks)} bloques verificados; {len(text.splitlines())} líneas')
