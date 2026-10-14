#include "W3DDevice/Common/DeckRadarGeometry.h"
#include <cassert>
#include <limits>
#include <iostream>
// GeneralsX @bugfix Codex 14/10/2026 North-up image, letterboxing and inverse map-input regression checks.
int main() {
    int x=-1,y=-1;
    using DeckRadarGeometry::toCells;
    assert(toCells(.25f,.125f,0,0,1,1,128,128,x,y) && x==32 && y==112);
    assert(toCells(.75f,.875f,0,0,1,1,128,128,x,y) && x==96 && y==16);
    assert(toCells(1,0,0,0,1,1,128,128,x,y) && x==127 && y==127);
    assert(toCells(0,1,0,0,1,1,128,128,x,y) && x==0 && y==0);
    assert(!toCells(.5f,.1f,0,.25f,1,.75f,128,128,x,y));
    assert(toCells(.5f,.25f,0,.25f,1,.75f,128,128,x,y) && x==64 && y==127);
    assert(toCells(.25f,.5f,.25f,0,.75f,1,128,128,x,y) && x==0 && y==64);
    assert(!toCells(.1f,.5f,.25f,0,.75f,1,128,128,x,y));
    assert(!toCells(std::numeric_limits<float>::quiet_NaN(),.5f,0,0,1,1,128,128,x,y));
    assert(!toCells(.5f,.5f,0,0,0,1,128,128,x,y));
    std::cout<<"Radar image/input orientation, map edges and aspect-fit tests passed\n";
}
