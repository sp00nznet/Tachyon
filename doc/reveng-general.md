# Time

The speed control (CFPS::GetSpeedFactor) is a function of the current game speed.
It's updated in CFPS::OnLoop via something similar to:

```c
LONGLONG freq, pc;
QueryPerformanceFrequency(&freq);
QueryPerformanceCounter(&pc);

double time = (double)pc / (double)freq * 1000.0;
double dt_ms = time - this->LastTime;
double dt_sec = dt_ms / 1000.0;
this->LastTime = time;

this->SpeedFactor = dt_sec * (this->speedLevel * 7 + 16.0);
```

Where `speedLevel` is a way to adjust the speed of the game from the dev console, and
defaults to zero. Thus `SpeedFactor` is sixteen times the delta-time.

You may therefore see something using the constant 0.0265 (1/16) to recover the seconds time:

```c
timer += SpeedFactor * 0.0625;
```
