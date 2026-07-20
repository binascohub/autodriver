#!/usr/bin/env bash
# Instala e executa o Autodriver em um celular Android conectado via USB (modo debug ativado).
set -euo pipefail

cd "$(dirname "$0")"

APPLICATION_ID="com.example.autodriver"
MAIN_ACTIVITY=".MainActivity"

if ! command -v adb >/dev/null 2>&1; then
    echo "adb não encontrado no PATH. Instale o Android SDK Platform-Tools." >&2
    exit 1
fi

echo "Verificando dispositivos conectados..."
DEVICES=$(adb devices | awk 'NR>1 && $2=="device" {print $1}')
if [ -z "$DEVICES" ]; then
    echo "Nenhum dispositivo Android detectado. Conecte o celular via USB e ative a depuração USB." >&2
    exit 1
fi
echo "Dispositivo(s) encontrado(s): $DEVICES"

echo "Compilando e instalando o app (debug)..."
./gradlew installDebug

echo "Abrindo o app no celular..."
adb shell am start -n "${APPLICATION_ID}/${MAIN_ACTIVITY}"

echo "Concluído."
