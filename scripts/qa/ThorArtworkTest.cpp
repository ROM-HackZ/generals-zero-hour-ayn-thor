// GeneralsX @feature Codex 14/10/2026 Verify atlas boundaries, alpha, letterboxing and retail rotated UV convention.
#include "SDL3Device/GameClient/ThorArtwork.h"
#include <cassert>
#include <iostream>
int main() {
    const std::vector<unsigned int> page = {0xffff0000,0x8000ff00,0xff0000ff,0xffffffff};
    auto all = ThorArtwork::crop(page,2,2,2,2,0,0,1,1,false);
    assert(all[0]==page[0] && all[127]==page[1] && all[127*128]==page[2] && all.back()==page[3]);
    auto region = ThorArtwork::crop(page,2,2,1,1,.5f,0,1,.5f,false);
    for(auto pixel:region) assert(pixel==0x8000ff00);
    auto rotated = ThorArtwork::crop(page,2,2,2,2,0,0,1,1,true);
    assert(rotated[0]==page[1] && rotated[127]==page[3] && rotated[127*128]==page[0] && rotated.back()==page[2]);
    auto wide = ThorArtwork::crop(page,2,2,2,1,0,0,1,.5f,false);
    assert(wide[0]==0 && wide[31*128]==0 && wide[32*128]==page[0] && wide[95*128+127]==page[1] && wide[96*128]==0);
    assert(ThorArtwork::crop({},0,0,1,1,0,0,1,1,false).empty());
    std::cout << "Artwork atlas, alpha, orientation and aspect tests passed\n";
}
