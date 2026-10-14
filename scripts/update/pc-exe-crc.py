#!/usr/bin/env python3
# GeneralsX @feature Android port 27/09/2026 The EXE checksum a PC GeneralsOnline client reports
# to lobbies, computed from its executable -- for update/config.json's pc_exe_crc, which the
# "Play with PC players" switch claims (GlobalData.cpp). Reproduces the Windows branch of
# GlobalData::generateExeCRC(): the engine's CRC (rotate left by one, add the byte) over the
# executable, then the version number (major << 16 | minor, little-endian; 1.4 for Zero Hour),
# then Data/Scripts/SkirmishScripts.scb and MultiplayerScripts.scb from the game folder.
#
# Checked against a device: the same function with version 1.0 and no executable gives exactly
# the checksum this port computes for itself (4265514697), so the scripts and the algorithm are
# right; 092226_QFE1's GeneralsOnlineZH_60.exe gives 524577083.
#
# usage: pc-exe-crc.py GeneralsOnlineZH_60.exe /path/to/ZeroHour
import os, struct, sys

M = 0xFFFFFFFF


def feed(crc, data):
    for b in data:
        crc = ((((crc << 1) | (crc >> 31)) & M) + b) & M
    return crc


def main():
    if len(sys.argv) != 3:
        sys.exit("usage: pc-exe-crc.py <PC executable> <Zero Hour folder with Data/Scripts>")
    exe, root = sys.argv[1], sys.argv[2]
    crc = feed(0, open(exe, "rb").read())
    crc = feed(crc, struct.pack("<I", (1 << 16) | 4))
    for name in ("SkirmishScripts.scb", "MultiplayerScripts.scb"):
        crc = feed(crc, open(os.path.join(root, "Data", "Scripts", name), "rb").read())
    print(crc)


if __name__ == "__main__":
    main()
