cmake_minimum_required(VERSION 3.22.1)
project("whisper_mel")

add_library(whisper_mel SHARED mel_spectrogram.cpp)

find_library(log-lib log)
target_link_libraries(whisper_mel ${log-lib} m)
