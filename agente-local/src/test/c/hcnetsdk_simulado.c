/*
 * Biblioteca nativa SIMULADA del SDK de Hikvision (HCNetSDK), solo para pruebas.
 * Declara las estructuras con el mismo orden y tamaño que HCNetSDK.h y verifica que el agente
 * (Java + JNA) envíe estructuras del tamaño exacto: si no coinciden, el SDK real tampoco funcionaría.
 *
 * Compilar en Linux: gcc -shared -fPIC -o libhcnetsdk.so hcnetsdk_simulado.c
 * Variable de entorno SIMULADOR_SDK_SIN_SOPORTE=1 → la búsqueda de eventos responde "no soportado".
 */
#include <string.h>
#include <stdlib.h>

typedef unsigned char BYTE; typedef unsigned short WORD; typedef unsigned int DWORD; typedef int BOOL; typedef int LONG;

typedef struct { DWORD dwYear, dwMonth, dwDay, dwHour, dwMinute, dwSecond; } NET_DVR_TIME;

typedef struct {
    char sDeviceAddress[129]; BYTE byUseTransport; WORD wPort; char sUserName[64]; char sPassword[64];
    void *cbLoginResult; void *pUser; BOOL bUseAsynLogin; BYTE byProxyType; BYTE byUseUTCTime; BYTE byLoginMode;
    BYTE byHttps; LONG iProxyID; BYTE byVerifyMode; BYTE byRes3[119];
} NET_DVR_USER_LOGIN_INFO;

typedef struct {
    DWORD dwSize; DWORD dwMajor; DWORD dwMinor; NET_DVR_TIME struStartTime; NET_DVR_TIME struEndTime;
    BYTE byCardNo[32]; BYTE byName[32]; BYTE byPicEnable; BYTE byTimeType; BYTE byRes2[2];
    DWORD dwBeginSerialNo; DWORD dwEndSerialNo; DWORD dwIOTChannelNo; WORD wInductiveEventType;
    BYTE bySearchType; BYTE byEventAttribute; char szMonitorID[64]; BYTE byEmployeeNo[32]; BYTE byRes[140];
} NET_DVR_ACS_EVENT_COND;

typedef struct { float fX, fY; } NET_VCA_POINT;
typedef struct { char sIpV4[16]; BYTE byIPv6[128]; } NET_DVR_IPADDR;

typedef struct {
    DWORD dwSize; BYTE byCardNo[32]; BYTE byCardType; BYTE byWhiteListNo; BYTE byReportChannel; BYTE byCardReaderKind;
    DWORD dwCardReaderNo; DWORD dwDoorNo; DWORD dwVerifyNo; DWORD dwAlarmInNo; DWORD dwAlarmOutNo; DWORD dwCaseSensorNo;
    DWORD dwRs485No; DWORD dwMultiCardGroupNo; WORD wAccessChannel; BYTE byDeviceNo; BYTE byDistractControlNo;
    DWORD dwEmployeeNo; WORD wLocalControllerID; BYTE byInternetAccess; BYTE byType; BYTE byMACAddr[6];
    BYTE bySwipeCardType; BYTE byEventAttribute; DWORD dwSerialNo; BYTE byChannelControllerID;
    BYTE byChannelControllerLampID; BYTE byChannelControllerIRAdaptorID; BYTE byChannelControllerIREmitterID;
    DWORD dwRecordChannelNum; char *pRecordChannelData; BYTE byUserType; BYTE byCurrentVerifyMode;
    BYTE byAttendanceStatus; BYTE byStatusValue; BYTE byEmployeeNo[32]; BYTE byRes1; BYTE byMask;
    BYTE byThermometryUnit; BYTE byIsAbnomalTemperature; float fCurrTemperature; NET_VCA_POINT struRegionCoordinates;
    BYTE byRes[48];
} NET_DVR_ACS_EVENT_DETAIL;

typedef struct {
    DWORD dwSize; DWORD dwMajor; DWORD dwMinor; NET_DVR_TIME struTime; BYTE sNetUser[16];
    NET_DVR_IPADDR struRemoteHostAddr; NET_DVR_ACS_EVENT_DETAIL struAcsEventInfo; DWORD dwPicDataLen; char *pPicData;
    WORD wInductiveEventType; BYTE byTimeType; BYTE byRes1; DWORD dwQRCodeInfoLen; DWORD dwVisibleLightDataLen;
    DWORD dwThermalDataLen; char *pQRCodeInfo; char *pVisibleLightData; char *pThermalData; BYTE byRes[36];
} NET_DVR_ACS_EVENT_CFG;

/* ---- Estado del simulador ---- */
static int ultimo_error = 0, inicializado = 0, sesiones_abiertas = 0, busquedas_abiertas = 0;
static int indice_evento = 0, espera_pendiente = 0;
static NET_DVR_TIME desde, hasta;

/* legajo texto, legajo numérico, minor, día relativo (0 = hoy configurado), hora, minuto */
typedef struct { const char *legajo_texto; DWORD legajo_numero; DWORD minor; DWORD anio, mes, dia, hora, minuto; BYTE modo; } EventoSimulado;
static EventoSimulado eventos[] = {
    {"101", 0, 38, 2026, 10, 15, 8, 0, 5},     /* huella aceptada */
    {"101", 0, 39, 2026, 10, 15, 8, 1, 5},     /* huella rechazada: no debe cargarse */
    {"", 0, 39, 2026, 10, 15, 8, 2, 5},        /* sin legajo: no debe cargarse */
    {"", 102, 1, 2026, 10, 15, 8, 5, 3},       /* firmware viejo: solo legajo numérico */
    {"101", 0, 38, 2026, 10, 15, 17, 0, 5},
    {"102", 0, 38, 2026, 10, 15, 17, 10, 5},
};
static const int cantidad_eventos = sizeof(eventos) / sizeof(eventos[0]);

static long clave_tiempo(NET_DVR_TIME *t) { return ((((long)t->dwYear * 13 + t->dwMonth) * 32 + t->dwDay) * 24 + t->dwHour) * 60 + t->dwMinute; }

/* Funciones de verificación para la prueba Java: tamaños según el compilador de C */
int SIMULADOR_TamanioCondicion() { return sizeof(NET_DVR_ACS_EVENT_COND); }
int SIMULADOR_TamanioEvento() { return sizeof(NET_DVR_ACS_EVENT_CFG); }
int SIMULADOR_TamanioInicioDeSesion() { return sizeof(NET_DVR_USER_LOGIN_INFO); }
int SIMULADOR_SesionesAbiertas() { return sesiones_abiertas; }
int SIMULADOR_BusquedasAbiertas() { return busquedas_abiertas; }

BOOL NET_DVR_Init() { inicializado = 1; return 1; }
BOOL NET_DVR_Cleanup() { inicializado = 0; return 1; }
BOOL NET_DVR_SetConnectTime(DWORD espera, DWORD intentos) { return 1; }
BOOL NET_DVR_SetReconnect(DWORD intervalo, BOOL habilitar) { return 1; }
BOOL NET_DVR_SetSDKInitCfg(int tipo, void *valor) { return 1; }
DWORD NET_DVR_GetLastError() { return ultimo_error; }

static LONG iniciar(const char *ip, WORD puerto, const char *usuario, const char *clave, void *info) {
    if (!inicializado) { ultimo_error = 3; return -1; }
    if (strcmp(ip, "127.0.0.1") != 0 || puerto != 8000) { ultimo_error = 7; return -1; }
    if (strcmp(usuario, "admin") != 0 || strcmp(clave, "Clave123") != 0) { ultimo_error = 1; return -1; }
    memset(info, 0, 48);
    strcpy((char *)info, "DS-K1A802MF20180709AAWR123456789");
    sesiones_abiertas++;
    return 7;
}

LONG NET_DVR_Login_V40(NET_DVR_USER_LOGIN_INFO *datos, void *info) {
    return iniciar(datos->sDeviceAddress, datos->wPort, datos->sUserName, datos->sPassword, info);
}

LONG NET_DVR_Login_V30(char *ip, WORD puerto, char *usuario, char *clave, void *info) {
    return iniciar(ip, puerto, usuario, clave, info);
}

BOOL NET_DVR_Logout(LONG sesion) { if (sesion == 7) sesiones_abiertas--; return 1; }

LONG NET_DVR_StartRemoteConfig(LONG sesion, DWORD comando, void *entrada, DWORD tamanio, void *retorno, void *usuario) {
    if (getenv("SIMULADOR_SDK_SIN_SOPORTE")) { ultimo_error = 23; return -1; }
    if (sesion != 7 || comando != 2514) { ultimo_error = 17; return -1; }
    NET_DVR_ACS_EVENT_COND *condicion = (NET_DVR_ACS_EVENT_COND *)entrada;
    /* El SDK real rechaza estructuras de tamaño distinto: esto valida el mapeo de JNA */
    if (tamanio != sizeof(NET_DVR_ACS_EVENT_COND) || condicion->dwSize != sizeof(NET_DVR_ACS_EVENT_COND)) { ultimo_error = 17; return -1; }
    if (condicion->dwMajor != 5) { ultimo_error = 17; return -1; }
    desde = condicion->struStartTime; hasta = condicion->struEndTime;
    indice_evento = 0; espera_pendiente = 1; busquedas_abiertas++;
    return 42;
}

LONG NET_DVR_GetNextRemoteConfig(LONG busqueda, void *salida, DWORD tamanio) {
    if (busqueda != 42 || tamanio != sizeof(NET_DVR_ACS_EVENT_CFG)) { ultimo_error = 17; return 1003; }
    if (espera_pendiente) { espera_pendiente = 0; return 1001; } /* simula "esperar" una vez */
    while (indice_evento < cantidad_eventos) {
        EventoSimulado *e = &eventos[indice_evento++];
        NET_DVR_TIME t = { e->anio, e->mes, e->dia, e->hora, e->minuto, 0 };
        if (clave_tiempo(&t) < clave_tiempo(&desde) || clave_tiempo(&t) > clave_tiempo(&hasta)) continue;
        NET_DVR_ACS_EVENT_CFG *evento = (NET_DVR_ACS_EVENT_CFG *)salida;
        if (evento->dwSize != sizeof(NET_DVR_ACS_EVENT_CFG)) { ultimo_error = 17; return 1003; }
        memset(evento, 0, sizeof(*evento));
        evento->dwSize = sizeof(*evento); evento->dwMajor = 5; evento->dwMinor = e->minor; evento->struTime = t;
        evento->struAcsEventInfo.dwEmployeeNo = e->legajo_numero;
        strncpy((char *)evento->struAcsEventInfo.byEmployeeNo, e->legajo_texto, 31);
        evento->struAcsEventInfo.byCurrentVerifyMode = e->modo;
        return 1000;
    }
    return 1002;
}

BOOL NET_DVR_StopRemoteConfig(LONG busqueda) { if (busqueda == 42) busquedas_abiertas--; return 1; }
