package com.example.autodriver;

// Runs inside the shell-privileged process Shizuku starts on our behalf, so
// it can issue a real `input tap` shell command — something a normal app
// process has no permission to do on its own.
interface IShizukuTapService {
    void tap(int x, int y);
    void destroy();
}
