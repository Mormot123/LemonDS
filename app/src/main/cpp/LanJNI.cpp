#include <jni.h>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#include "net/LAN.h"
#include "net/MPInterface.h"

using namespace melonDS;

// Declared in MelonDS.h. Repeated here so this file does not pull in the renderer and EGL headers.
namespace MelonDSAndroid
{
    extern bool hasActiveInstance();
}

// These functions are called from the lobby's threads (UI thread plus a pump and a refresh thread).
// They are serialized with one mutex so the LAN object is never destroyed or replaced while another
// lobby thread is using it. The emulator thread does not take this mutex, so the lobby must not run
// it against a live game: setLanEnabled refuses to run, and processLobby does nothing, while an
// emulator instance exists.

namespace
{
    std::mutex lanMutex;

#define LAN_LOCK() std::lock_guard<std::mutex> lanGuard(lanMutex)

    LAN* currentLan()
    {
        if (MPInterface::GetType() != MPInterface_LAN)
            return nullptr;

        return static_cast<LAN*>(&MPInterface::Get());
    }

    jobjectArray toJavaStringArray(JNIEnv* env, const std::vector<std::string>& values)
    {
        jclass stringClass = env->FindClass("java/lang/String");
        jobjectArray result = env->NewObjectArray((jsize) values.size(), stringClass, nullptr);
        for (jsize i = 0; i < (jsize) values.size(); i++)
        {
            jstring value = env->NewStringUTF(values[i].c_str());
            env->SetObjectArrayElement(result, i, value);
            env->DeleteLocalRef(value);
        }
        env->DeleteLocalRef(stringClass);
        return result;
    }

    std::string readString(JNIEnv* env, jstring value)
    {
        if (value == nullptr)
            return std::string();

        const char* chars = env->GetStringUTFChars(value, nullptr);
        std::string result(chars);
        env->ReleaseStringUTFChars(value, chars);
        return result;
    }
}

extern "C"
{
// Switches the multiplayer backend between LAN and none. Call with true before hosting,
// joining or discovering, and with false once the session is over.
JNIEXPORT void JNICALL
Java_me_magnum_melonds_LanInterface_setLanEnabled(JNIEnv* env, jobject thiz, jboolean enabled)
{
    LAN_LOCK();

    // Replacing the interface destroys the old one, which the emulator thread may still be using
    if (MelonDSAndroid::hasActiveInstance())
        return;

    MPInterface::Set(enabled ? MPInterface_LAN : MPInterface_Dummy);
}

// The core only services discovery beacons and the ENet handshake from Process(), which the emulator
// thread calls once per frame. While a game is running that thread does the work. In the lobby there
// is no game, so the lobby calls this on a timer. It does nothing while an emulator instance exists,
// because Process() is not safe to call from two threads at once.
JNIEXPORT void JNICALL
Java_me_magnum_melonds_LanInterface_processLobby(JNIEnv* env, jobject thiz)
{
    LAN_LOCK();

    if (MelonDSAndroid::hasActiveInstance())
        return;

    LAN* lan = currentLan();
    if (lan)
        lan->Process();
}

JNIEXPORT jboolean JNICALL
Java_me_magnum_melonds_LanInterface_startHost(JNIEnv* env, jobject thiz, jstring playerName, jint maxPlayers)
{
    LAN_LOCK();
    LAN* lan = currentLan();
    if (!lan)
        return JNI_FALSE;

    std::string name = readString(env, playerName);
    return lan->StartHost(name.c_str(), maxPlayers) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_me_magnum_melonds_LanInterface_startClient(JNIEnv* env, jobject thiz, jstring playerName, jstring hostAddress)
{
    LAN_LOCK();
    LAN* lan = currentLan();
    if (!lan)
        return JNI_FALSE;

    std::string name = readString(env, playerName);
    std::string host = readString(env, hostAddress);
    return lan->StartClient(name.c_str(), host.c_str()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_me_magnum_melonds_LanInterface_startDiscovery(JNIEnv* env, jobject thiz)
{
    LAN_LOCK();
    LAN* lan = currentLan();
    return (lan && lan->StartDiscovery()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_LanInterface_endDiscovery(JNIEnv* env, jobject thiz)
{
    LAN_LOCK();
    LAN* lan = currentLan();
    if (lan)
        lan->EndDiscovery();
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_LanInterface_endSession(JNIEnv* env, jobject thiz)
{
    LAN_LOCK();
    LAN* lan = currentLan();
    if (lan)
        lan->EndSession();
}

// Each entry is "ipv4<TAB>sessionName<TAB>numPlayers<TAB>maxPlayers<TAB>status" where status is 0 (idle) or 1 (playing)
JNIEXPORT jobjectArray JNICALL
Java_me_magnum_melonds_LanInterface_getDiscoveryList(JNIEnv* env, jobject thiz)
{
    LAN_LOCK();
    std::vector<std::string> entries;
    LAN* lan = currentLan();
    if (lan)
    {
        for (const auto& [key, data] : lan->GetDiscoveryList())
        {
            std::string ip = std::to_string(key >> 24) + "." + std::to_string((key >> 16) & 0xFF) + "." +
                             std::to_string((key >> 8) & 0xFF) + "." + std::to_string(key & 0xFF);

            // SessionName comes from the network, so bound it explicitly
            std::string name(data.SessionName, strnlen(data.SessionName, sizeof(data.SessionName)));

            entries.push_back(ip + "\t" + name + "\t" + std::to_string(data.NumPlayers) + "\t" +
                              std::to_string(data.MaxPlayers) + "\t" + std::to_string(data.Status));
        }
    }
    return toJavaStringArray(env, entries);
}

// Each entry is "id<TAB>name<TAB>status<TAB>pingMs<TAB>isLocal" where status matches LAN::PlayerStatus
JNIEXPORT jobjectArray JNICALL
Java_me_magnum_melonds_LanInterface_getPlayerList(JNIEnv* env, jobject thiz)
{
    LAN_LOCK();
    std::vector<std::string> entries;
    LAN* lan = currentLan();
    if (lan)
    {
        for (const auto& player : lan->GetPlayerList())
        {
            if (player.Status == LAN::Player_None)
                continue;

            std::string name(player.Name, strnlen(player.Name, sizeof(player.Name)));
            entries.push_back(std::to_string(player.ID) + "\t" + name + "\t" + std::to_string((int) player.Status) + "\t" +
                              std::to_string(player.Ping) + "\t" + (player.IsLocalPlayer ? "1" : "0"));
        }
    }
    return toJavaStringArray(env, entries);
}

JNIEXPORT jint JNICALL
Java_me_magnum_melonds_LanInterface_getPlayerCount(JNIEnv* env, jobject thiz)
{
    LAN_LOCK();
    LAN* lan = currentLan();
    return lan ? lan->GetNumPlayers() : 0;
}
}
