// GeneralsX @feature Codex 14/10/2026 Native controller intentions and immutable deck snapshots.
#include "SDL3Device/GameClient/ThorInput.h"
#include "SDL3Device/GameClient/ThorArtwork.h"
#if defined(__ANDROID__)
#include <jni.h>
#include <android/log.h>
#include <SDL3/SDL.h>
#include <mutex>
#include <deque>
#include <sstream>
#include <string>
#include <cmath>
#include <algorithm>
#include <vector>
#include <map>
#include <set>
#include <cctype>
#include "GameClient/Image.h"
#include "Common/PlayerTemplate.h"
#include "Common/SpecialPower.h"
#include "WW3D2/assetmgr.h"
#include "WW3D2/texture.h"
#include "WW3D2/surfaceclass.h"
#include "SDL3Device/GameClient/TouchInput.h"
#include "Common/MessageStream.h"
#include "Common/Player.h"
#include "Common/PlayerList.h"
#include "Common/Money.h"
#include "Common/Energy.h"
#include "GameClient/GameText.h"
#include "Common/ThingTemplate.h"
#include "GameClient/ControlBar.h"
#include "GameClient/GameClient.h"
#include "GameClient/Drawable.h"
#include "GameClient/GameWindow.h"
#include "GameClient/GameWindowManager.h"
#include "GameClient/Gadget.h"
#include "GameClient/GadgetPushButton.h"
#include "GameClient/GadgetComboBox.h"
#include "GameClient/GadgetCheckBox.h"
#include "GameClient/GadgetSlider.h"
#include "GameClient/GadgetTextEntry.h"
#include "GameClient/KeyDefs.h"
#include "GameLogic/Squad.h"
#include "GameLogic/Module/ProductionUpdate.h"
#include "GameLogic/Module/SpecialPowerModule.h"
#include "GameLogic/Module/ContainModule.h"
#include "GameLogic/Module/OCLUpdate.h"
#include "Common/Upgrade.h"
#include "GameClient/InGameUI.h"
#include "GameClient/View.h"
#include "GameClient/Display.h"
#include "GameLogic/GameLogic.h"
#include "GameLogic/Object.h"
#include "GameLogic/Module/BodyModule.h"
#include "Common/Radar.h"
#include "Common/GameCommon.h"
#include "Common/GameUtility.h"
#include "Common/GlobalData.h"
#include "Common/NameKeyGenerator.h"
#include "GameClient/SelectionXlat.h"
#include "GameClient/CommandXlat.h"
#include "W3DDevice/Common/W3DRadar.h"

namespace {
struct Intent { int type, value; float x, y; std::string action, activation; };
std::mutex bridgeMutex;
std::deque<Intent> pending;
std::string published = "{\"ready\":false}";
float stick[4] = {};
float smoothPan[2] = {};
bool precision = false, waypoint = false, enabled = false, suspended = false, axesEnabled = false;
float aimX = .5f, aimY = .5f;
Uint64 lastPump = 0, lastSnapshot = 0, lastRadar = 0;
int editorId = 0;
std::vector<unsigned int> radarPixels;
std::vector<WideChar> pendingText;
bool textReady = false;
bool boxHeld = false;
bool snapSetting = true;
bool externalDeckSetting = false;
bool viewportExpanded = false;
Uint64 boxDown = 0;
Coord3D boxAnchor{};

// GeneralsX @feature Codex 14/10/2026 Bounded, engine-thread artwork extraction for both displays.
struct IconCopy { std::vector<unsigned int> pixels; Uint64 used = 0; };
struct PageCopy { int width, height; std::vector<unsigned int> pixels; Uint64 used; };
std::map<std::string, IconCopy> iconCopies;
std::map<std::string, PageCopy> pageCopies;
std::map<int, std::string> windowNames;
std::deque<std::string> iconRequests;
std::set<std::string> requestedIcons;
unsigned iconGeneration = 1;
constexpr size_t pageBudget = 8 * 1024 * 1024;
constexpr size_t iconLimit = 128;

std::string imageKey(const Image *image) { return image ? image->getName().str() : ""; }
unsigned pixelAlpha(WW3DFormat format, const unsigned char *p) {
    switch (format) {
    case WW3D_FORMAT_A8R8G8B8: return p[3];
    case WW3D_FORMAT_A4R4G4B4: return (p[1] >> 4) * 17;
    case WW3D_FORMAT_A1R5G5B5: return p[1] & 128 ? 255 : 0;
    case WW3D_FORMAT_A8: return p[0];
    case WW3D_FORMAT_A8L8: return p[1];
    default: return 255;
    }
}
PageCopy *copyImagePage(const Image *image) {
    if (!image || !WW3DAssetManager::Get_Instance()) return nullptr;
    const bool raw = image->getStatus() & IMAGE_STATUS_RAW_TEXTURE;
    std::string key = raw ? "raw:" + imageKey(image) : image->getFilename().str();
    auto cached = pageCopies.find(key);
    if (cached != pageCopies.end()) { cached->second.used = SDL_GetTicks(); return &cached->second; }
    TextureClass *texture = raw ? (TextureClass *)image->getRawTextureData() :
        WW3DAssetManager::Get_Instance()->Get_Texture(image->getFilename().str(), MIP_LEVELS_1);
    if (!texture) return nullptr;
    SurfaceClass *surface = texture->Get_Surface_Level();
    if (!raw) texture->Release_Ref();
    if (!surface) return nullptr;
    SurfaceClass::SurfaceDescription desc;
    surface->Get_Description(desc);
    if (desc.Width <= 0 || desc.Height <= 0 || size_t(desc.Width) * desc.Height * 4 > pageBudget) {
        surface->Release_Ref(); return nullptr;
    }
    if (desc.Format >= WW3D_FORMAT_DXT1 && desc.Format <= WW3D_FORMAT_DXT5) {
        SurfaceClass *converted = new SurfaceClass(desc.Width, desc.Height, WW3D_FORMAT_A8R8G8B8);
        converted->Copy(0, 0, 0, 0, desc.Width, desc.Height, surface);
        surface->Release_Ref(); surface = converted; surface->Get_Description(desc);
    }
    const unsigned bytes = Get_Bytes_Per_Pixel(desc.Format);
    int pitch = 0;
    void *bits = bytes ? surface->Lock(&pitch) : nullptr;
    if (!bits) { surface->Release_Ref(); return nullptr; }
    PageCopy page{int(desc.Width), int(desc.Height), {}, SDL_GetTicks()};
    page.pixels.resize(size_t(desc.Width) * desc.Height);
    for (int y = 0; y < page.height; ++y) for (int x = 0; x < page.width; ++x) {
        Vector3 rgb; surface->Get_Pixel(rgb, x, y, bits, pitch);
        const auto *pixel = static_cast<const unsigned char *>(bits) + y * pitch + x * bytes;
        unsigned r = std::max(0, std::min(255, int(rgb.X * 255 + .5f)));
        unsigned g = std::max(0, std::min(255, int(rgb.Y * 255 + .5f)));
        unsigned b = std::max(0, std::min(255, int(rgb.Z * 255 + .5f)));
        page.pixels[y * page.width + x] = (pixelAlpha(desc.Format, pixel) << 24) | (r << 16) | (g << 8) | b;
    }
    surface->Unlock(); surface->Release_Ref();
    size_t total = page.pixels.size() * 4;
    for (const auto &entry : pageCopies) total += entry.second.pixels.size() * 4;
    while (total > pageBudget && !pageCopies.empty()) {
        auto oldest = std::min_element(pageCopies.begin(), pageCopies.end(), [](const auto &a, const auto &b) { return a.second.used < b.second.used; });
        total -= oldest->second.pixels.size() * 4; pageCopies.erase(oldest);
    }
    return &pageCopies.emplace(key, std::move(page)).first->second;
}
std::vector<unsigned int> copyIcon(const std::string &key) {
    const Image *image = TheMappedImageCollection ? TheMappedImageCollection->findImageByName(key.c_str()) : nullptr;
    PageCopy *page = copyImagePage(image);
    if (!page || !image || image->getImageWidth() <= 0 || image->getImageHeight() <= 0) return {};
    const Region2D *uv = image->getUV();
    const bool rotated = image->getStatus() & IMAGE_STATUS_ROTATED_90_CLOCKWISE;
    return ThorArtwork::crop(page->pixels, page->width, page->height, image->getImageWidth(), image->getImageHeight(),
        uv->lo.x, uv->lo.y, uv->hi.x, uv->hi.y, rotated);
}
void pumpIcons() {
    for (int i = 0; i < 2; ++i) {
        std::string key;
        { std::lock_guard<std::mutex> guard(bridgeMutex); if (iconRequests.empty()) return; key = iconRequests.front(); iconRequests.pop_front(); }
        auto pixels = copyIcon(key);
        std::lock_guard<std::mutex> guard(bridgeMutex);
        requestedIcons.erase(key);
        if (iconCopies.size() >= iconLimit) {
            auto oldest = std::min_element(iconCopies.begin(), iconCopies.end(), [](const auto &a, const auto &b) { return a.second.used < b.second.used; });
            iconCopies.erase(oldest);
        }
        iconCopies[key] = {std::move(pixels), SDL_GetTicks()};
    }
}

std::string utf8(const UnicodeString &text) {
    std::string result;
    const auto *p = text.str();
    for (; p && *p; ++p) {
        unsigned c = static_cast<unsigned>(*p);
        if (c >= 0xd800 && c <= 0xdbff && p[1] >= 0xdc00 && p[1] <= 0xdfff)
            c = 0x10000 + ((c - 0xd800) << 10) + (static_cast<unsigned>(*++p) - 0xdc00);
        if (c < 128) result += char(c);
        else if (c < 2048) { result += char(0xc0 | (c >> 6)); result += char(0x80 | (c & 63)); }
        else if (c < 65536) { result += char(0xe0 | (c >> 12)); result += char(0x80 | ((c >> 6) & 63)); result += char(0x80 | (c & 63)); }
        else { result += char(0xf0 | (c >> 18)); result += char(0x80 | ((c >> 12) & 63)); result += char(0x80 | ((c >> 6) & 63)); result += char(0x80 | (c & 63)); }
    }
    return result;
}
std::string quoted(const std::string &s) {
    std::ostringstream out;
    out << '"';
    for (unsigned char c : s) {
        if (c == '"' || c == '\\') out << '\\' << c;
        else if (c == '\n') out << "\\n";
        else if (c == '\r') out << "\\r";
        else if (c == '\t') out << "\\t";
        else if (c >= 32) out << c;
    }
    out << '"';
    return out.str();
}
bool visible(GameWindow *w) {
    for (auto *p = w; p; p = p->winGetParent()) if (p->winIsHidden()) return false;
    return w != nullptr;
}
bool available(GameWindow *w) {
    for (auto *p = w; p; p = p->winGetParent()) if (!p->winGetEnabled()) return false;
    return w != nullptr;
}
bool presented(GameWindow *w) {
    for (auto *p = w; p; p = p->winGetParent()) if (!p->winIsPresentedHere()) return false;
    return w != nullptr;
}
bool inMatch() {
    return TheGameLogic && TheGameLogic->getGameMode() != GAME_NONE &&
        TheGameLogic->getGameMode() != GAME_SHELL;
}
bool battlefield() { return inMatch() && !TheGameLogic->isGamePaused(); }
GameWindow *menuRoot(const char *buttonName) {
    if (!TheWindowManager || !TheNameKeyGenerator) return nullptr;
    auto *w = TheWindowManager->winGetWindowFromId(nullptr, TheNameKeyGenerator->nameToKey(buttonName));
    if (!visible(w)) return nullptr;
    while (w->winGetParent()) w = w->winGetParent();
    return w;
}
void key(SDL_Scancode code) {
    if (!TheWindowManager) return;
    auto *field = editorId ? TheWindowManager->winGetWindowFromId(nullptr, editorId) : TheWindowManager->winGetFocus();
    if (!field || !visible(field)) return;
    const unsigned keyCode = code == SDL_SCANCODE_UP ? KEY_UP : code == SDL_SCANCODE_DOWN ? KEY_DOWN :
        code == SDL_SCANCODE_LEFT ? KEY_LEFT : code == SDL_SCANCODE_RIGHT ? KEY_RIGHT :
        code == SDL_SCANCODE_RETURN ? KEY_ENTER : KEY_ESC;
    TheWindowManager->winSendInputMsg(field, GWM_CHAR, keyCode, KEY_STATE_DOWN);
    TheWindowManager->winSendInputMsg(field, GWM_CHAR, keyCode, KEY_STATE_UP);
}
void message(GameMessage::Type type) {
    if (TheMessageStream) TheMessageStream->appendMessage(type);
}
void click(int x, int y) {
    if (!TheMessageStream) return;
    IRegion2D pos{{x, y}, {x, y}};
    GameMessage *m = TheMessageStream->appendMessage(GameMessage::MSG_MOUSE_LEFT_CLICK);
    m->appendPixelRegionArgument(pos);
    m->appendIntegerArgument(0);
}
// GeneralsX @bugfix Codex 14/10/2026 Preserve every visible action and bind occupant/queue activation to its current target.
const CommandButton *registeredCommand(GameWindow *w) {
    if (!TheControlBar || !(w->winGetStyle() & GWS_PUSH_BUTTON)) return nullptr;
    const void *data = GadgetButtonGetData(w);
    for (auto *candidate = TheControlBar->getCommandButtons(); candidate; candidate = candidate->getNext())
        if (data == candidate) return candidate;
    return nullptr;
}
bool withinContext(GameWindow *w, ContextParent context) {
    auto *root = TheControlBar ? TheControlBar->getContextParent(context) : nullptr;
    for (auto *p = w; p; p = p->winGetParent()) if (root && p == root) return true;
    return false;
}
const std::string &windowName(GameWindow *w) {
    const int id = w->winGetWindowId();
    auto found = windowNames.find(id);
    if (found == windowNames.end()) found = windowNames.emplace(id, TheNameKeyGenerator->keyToName(static_cast<NameKeyType>(id)).str()).first;
    return found->second;
}
std::string controlLabel(GameWindow *w) {
    std::string name = windowName(w);
    const auto colon = name.find_last_of(':'); if (colon != std::string::npos) name.erase(0, colon + 1);
    for (const char *prefix : {"Button", "ComboBox", "CheckBox", "RadioButton", "Slider", "TextEntry", "Entry", "ListBox"})
        if (name.rfind(prefix, 0) == 0) { name.erase(0, std::char_traits<char>::length(prefix)); break; }
    std::string label;
    for (size_t i = 0; i < name.size(); ++i) {
        const auto c = static_cast<unsigned char>(name[i]);
        if (i && std::isupper(c) && std::islower(static_cast<unsigned char>(name[i - 1]))) label += ' ';
        label += c == '_' ? ' ' : static_cast<char>(c);
    }
    return label.empty() ? "Control" : label;
}
const ProductionEntry *queueEntry(GameWindow *w) {
    if (!TheControlBar || !TheInGameUI || !available(w)) return nullptr;
    int index = -1;
    for (int i = 0; i < MAX_BUILD_QUEUE_BUTTONS; ++i) if (TheControlBar->getQueueWindow(i) == w) index = i;
    auto *draw = TheInGameUI->getFirstSelectedDrawable();
    auto *production = draw && draw->getObject() ? ProductionUpdate::getProductionUpdateInterfaceFromObject(draw->getObject()) : nullptr;
    if (index < 0 || !production) return nullptr;
    auto *entry = production->firstProduction();
    for (int i = 0; entry && i < index; ++i) entry = production->nextProduction(entry);
    return entry;
}
std::string activationKey(GameWindow *w, const CommandButton *cmd) {
    std::string key = cmd ? cmd->getName().str() : "window:" + std::to_string(w->winGetWindowId());
    if (withinContext(w, CP_MASTER) && TheInGameUI) {
        auto *draw = TheInGameUI->getFirstSelectedDrawable();
        key += "@" + std::to_string(draw && draw->getObject() ? draw->getObject()->getID() : INVALID_ID);
    }
    if (cmd && cmd->getCommandType() == GUI_COMMAND_EXIT_CONTAINER)
        key += ":occupant:" + std::to_string(TheControlBar->getContainedObjectID(w));
    if (auto *entry = queueEntry(w)) key += ":production:" + std::to_string(entry->getProductionID());
    return key;
}
void activate(int id, const std::string &expectedAction = {}, const std::string &expectedActivation = {}) {
    if (!TheWindowManager) return;
    auto *w = TheWindowManager->winGetWindowFromId(nullptr, id);
    if (!visible(w) || !available(w)) return;
    if (auto *modal = TheWindowManager->winGetModalWindow()) {
        bool inside = false;
        for (auto *p = w; p; p = p->winGetParent()) if (p == modal) inside = true;
        if (!inside) return;
    }
    const auto *command = registeredCommand(w);
    if (!expectedActivation.empty() && expectedActivation != activationKey(w, command)) return;
    __android_log_print(ANDROID_LOG_DEBUG, "ThorInput", "Activate window id=%d style=%u", id, w->winGetStyle());
    // GeneralsX @bugfix Codex 14/10/2026 Only registered command data belongs to ControlBar.
    if (TheControlBar && (w->winGetStyle() & GWS_PUSH_BUTTON)) {
        const void *data = GadgetButtonGetData(w);
        for (auto *candidate = TheControlBar->getCommandButtons(); candidate; candidate = candidate->getNext()) {
            if (data == candidate) {
                if (!expectedAction.empty() && expectedAction != candidate->getName().str()) return;
                if (candidate->getCommandType() != GUI_COMMAND_NONE && TheControlBar->processContextSensitiveButtonClick(w, GBM_SELECTED) == CBC_COMMAND_USED) return;
                break;
            }
        }
    }
    if (!expectedAction.empty()) {
        bool registered = false;
        if (TheControlBar && (w->winGetStyle() & GWS_PUSH_BUTTON))
            for (auto *candidate = TheControlBar->getCommandButtons(); candidate; candidate = candidate->getNext())
                if (GadgetButtonGetData(w) == candidate && expectedAction == candidate->getName().str()) registered = true;
        if (!registered) return;
    }
    if ((w->winGetStyle() & GWS_PUSH_BUTTON) && !(w->winGetStatus() & WIN_STATUS_CHECK_LIKE)) {
        TheWindowManager->winSendSystemMsg(w->winGetOwner(), GBM_SELECTED, reinterpret_cast<WindowMsgData>(w), 0);
        return;
    }
    int x, y, width, height;
    editorId = id;
    w->winGetScreenPosition(&x, &y); w->winGetSize(&width, &height);
    TheWindowManager->winSetFocus(w);
    // GeneralsX @bugfix Codex 14/10/2026 Entering an editor must preserve its current value.
    if (w->winGetStyle() & (GWS_COMBO_BOX | GWS_ENTRY_FIELD | GWS_SCROLL_LISTBOX | GWS_ALL_SLIDER)) return;
    const auto packed = static_cast<WindowMsgData>(((y + height / 2) << 16) | ((x + width / 2) & 65535));
    TheWindowManager->winSendInputMsg(w, GWM_LEFT_DOWN, packed, 0);
    TheWindowManager->winSendInputMsg(w, GWM_LEFT_UP, packed, 0);
}
void center() {
    if (!TheInGameUI || !TheTacticalView) return;
    auto *draw = TheInGameUI->getFirstSelectedDrawable();
    if (draw) TheTacticalView->userLookAt(draw->getPosition());
}
void cycle(int category) {
    if (category == 0) { message(GameMessage::MSG_META_SELECT_NEXT_IDLE_WORKER); return; }
    if (!TheGameClient || !TheInGameUI) return;
    auto *current = TheInGameUI->getFirstSelectedDrawable();
    std::vector<Drawable *> choices;
    for (auto *d = TheGameClient->getDrawableList(); d; d = d->getNextDrawable()) {
        auto *o = d->getObject();
        if (!o || !o->isLocallyControlled() || o->isEffectivelyDead() || !CanSelectDrawable(d, FALSE)) continue;
        bool match = category == 1 ? o->isKindOf(KINDOF_COMMANDCENTER) :
            category == 2 ? (!o->isKindOf(KINDOF_STRUCTURE) && !o->isKindOf(KINDOF_DOZER) && !o->isKindOf(KINDOF_HARVESTER)) :
            o->isKindOf(KINDOF_FS_FACTORY);
        if (match) choices.push_back(d);
    }
    if (choices.empty()) return;
    auto found = std::find(choices.begin(), choices.end(), current);
    auto *d = found == choices.end() || ++found == choices.end() ? choices.front() : *found;
    TheInGameUI->deselectAllDrawables(false);
    TheInGameUI->selectDrawable(d);
    auto *m = TheMessageStream->appendMessage(GameMessage::MSG_CREATE_SELECTED_GROUP);
    m->appendBooleanArgument(TRUE); m->appendObjectIDArgument(d->getObject()->getID());
    center();
}
void widgets(GameWindow *head, std::ostringstream &out, bool &first, int depth = 0, bool siblings = true) {
    if (depth > 32) return;
    for (auto *w = head; w; w = siblings ? w->winGetNext() : nullptr) {
        if (!visible(w)) continue;
        if (!battlefield() && !w->winIsPresentedHere()) continue;
        const unsigned style = w->winGetStyle();
        // GeneralsX @bugfix Codex 14/10/2026 Composite children are not independent controller targets.
        if (w->winGetWindowId() != 0 && (style & (GWS_PUSH_BUTTON | GWS_RADIO_BUTTON | GWS_CHECK_BOX | GWS_COMBO_BOX | GWS_ENTRY_FIELD | GWS_SCROLL_LISTBOX | GWS_ALL_SLIDER))) {
            int x, y, width, height;
            w->winGetScreenPosition(&x, &y); w->winGetSize(&width, &height);
            if (width > 0 && height > 0) {
                if (!first) out << ','; first = false;
                std::string label = utf8(w->winGetText());
                if (style & GWS_COMBO_BOX) label = utf8(GadgetComboBoxGetText(w));
                else if (style & GWS_ENTRY_FIELD) label = utf8(GadgetTextEntryGetText(w));
                const CommandButton *cmd = registeredCommand(w);
                if (label.empty()) label = utf8(w->winGetInstanceData()->getTooltipText());
                if (!cmd && label.empty()) label = controlLabel(w);
                if (cmd && TheGameText && !cmd->getTextLabel().isEmpty()) label = utf8(TheGameText->fetch(cmd->getTextLabel()));
                UnicodeString scienceName, scienceDescription;
                ScienceType science = SCIENCE_INVALID;
                if (cmd && cmd->getCommandType() == GUI_COMMAND_PURCHASE_SCIENCE && TheScienceStore && !cmd->getScienceVec().empty()) {
                    science = cmd->getScienceVec().front();
                    Player *local = ThePlayerList ? ThePlayerList->getLocalPlayer() : nullptr;
                    for (ScienceType candidate : cmd->getScienceVec()) if (!local || !local->hasScience(candidate)) { science = candidate; break; }
                    if (TheScienceStore->getNameAndDescription(science, scienceName, scienceDescription)) label = utf8(scienceName);
                }
                if (label.empty() && cmd) label = cmd->getName().str();
                label.erase(std::remove(label.begin(), label.end(), '&'), label.end());
                bool contextCommand = cmd && cmd->getCommandType() != GUI_COMMAND_NONE;
                int commandSlot = -1;
                int queueIndex = -1;
                if (cmd && TheControlBar) {
                    for (int index = 0; index < MAX_COMMANDS_PER_SET; ++index)
                        if (TheControlBar->getCommandWindow(index) == w) { contextCommand = true; commandSlot = index; }
                    // GeneralsX @bugfix Codex 14/10/2026 Export every registered action, including dedicated timer/construction panels.
                    if (cmd->getCommandType() == GUI_COMMAND_DOZER_CONSTRUCT_CANCEL || withinContext(w, CP_OCL_TIMER)) commandSlot = 0;
                    const std::string name = cmd->getName().str();
                    if (name == "NonCommand_Communicator" || name == "NonCommand_BriefingHistory" || name == "NonCommand_Beacon") { contextCommand = true; commandSlot = 1500; }
                    for (int index = 0; index < MAX_BUILD_QUEUE_BUTTONS; ++index)
                        if (TheControlBar->getQueueWindow(index) == w) queueIndex = index;
                }
                const ProductionEntry *entry = queueEntry(w);
                if (queueIndex >= 0) contextCommand = false;
                // GeneralsX @feature Codex 14/10/2026 Beacon editing, observer controls and live dialogs retain their original callbacks.
                const bool auxiliary = !cmd && (withinContext(w, CP_BEACON) || withinContext(w, CP_OBSERVER_INFO) || withinContext(w, CP_OBSERVER_LIST) || (battlefield() && presented(w) && !withinContext(w, CP_MASTER) && windowName(w).rfind("ControlBar.wnd:", 0) != 0 && windowName(w).rfind("GroupPanel.wnd:", 0) != 0));
                if (auxiliary) { contextCommand = true; commandSlot = 2000; }
                if (entry) {
                    if (entry && entry->getProductionType() == PRODUCTION_UNIT && entry->getProductionObject()) label = "Cancel " + utf8(entry->getProductionObject()->getDisplayName());
                    else if (entry && entry->getProductionType() == PRODUCTION_UPGRADE && entry->getProductionUpgrade() && TheGameText) label = "Cancel " + utf8(TheGameText->fetch(entry->getProductionUpgrade()->getDisplayNameLabel()));
                }
                const Object *occupant = cmd && cmd->getCommandType() == GUI_COMMAND_EXIT_CONTAINER && TheGameLogic ? TheGameLogic->findObjectByID(TheControlBar->getContainedObjectID(w)) : nullptr;
                if (occupant) label = "Exit " + utf8(occupant->getTemplate()->getDisplayName()) + " · " + std::to_string(commandSlot + 1);
                else if (cmd && cmd->getCommandType() == GUI_COMMAND_EXIT_CONTAINER) label = "Empty passenger slot " + std::to_string(commandSlot + 1);
                if (auxiliary && label.empty()) label = (style & GWS_ENTRY_FIELD) ? "Beacon text" : "Control";
                out << "{\"id\":" << w->winGetWindowId() << ",\"label\":" << quoted(label)
                    << ",\"enabled\":" << (available(w) && !(cmd && cmd->getCommandType() == GUI_COMMAND_EXIT_CONTAINER && !occupant) ? "true" : "false")
                    << ",\"command\":" << (contextCommand ? "true" : "false") << ",\"style\":" << style
                    << ",\"queue\":" << (entry ? "true" : "false")
                    << ",\"queueSlot\":" << (queueIndex >= 0 ? "true" : "false")
                    << ",\"x\":" << x << ",\"y\":" << y << ",\"w\":" << width << ",\"h\":" << height;
                const Image *icon = cmd ? cmd->getButtonImage() : w->winGetEnabledImage(0);
                if (cmd && cmd->getCommandType() == GUI_COMMAND_EXIT_CONTAINER) icon = w->winGetEnabledImage(0);
                if (entry && entry->getProductionType() == PRODUCTION_UNIT && entry->getProductionObject()) icon = entry->getProductionObject()->getButtonImage();
                if (entry && entry->getProductionType() == PRODUCTION_UPGRADE && entry->getProductionUpgrade()) icon = entry->getProductionUpgrade()->getButtonImage();
                out << ",\"icon\":" << quoted(imageKey(icon)) << ",\"slot\":" << commandSlot;
                if (style & GWS_ENTRY_FIELD) out << ",\"value\":" << quoted(utf8(GadgetTextEntryGetText(w)));
                if (style & (GWS_CHECK_BOX | GWS_RADIO_BUTTON)) out << ",\"checked\":" << ((style & GWS_CHECK_BOX) ? GadgetCheckBoxIsChecked(w) ? "true" : "false" : (w->winGetInstanceData()->getState() & WIN_STATE_SELECTED) ? "true" : "false");
                if (style & GWS_ALL_SLIDER) {
                    Int minimum, maximum; GadgetSliderGetMinMax(w, &minimum, &maximum);
                    out << ",\"sliderValue\":" << GadgetSliderGetPosition(w) << ",\"sliderMin\":" << minimum << ",\"sliderMax\":" << maximum;
                }
                if (cmd) out << ",\"action\":" << quoted(cmd->getName().str());
                if (cmd) out << ",\"commandType\":" << cmd->getCommandType();
                std::string identity = cmd ? cmd->getSpecialPowerTemplate() ? "power:" + std::string(cmd->getSpecialPowerTemplate()->getName().str()) : cmd->getName().str() : "window:" + std::to_string(w->winGetWindowId());
                if (cmd && cmd->getCommandType() == GUI_COMMAND_EXIT_CONTAINER) identity += ":slot:" + std::to_string(commandSlot);
                out << ",\"identity\":" << quoted(identity) << ",\"activation\":" << quoted(activationKey(w, cmd));
                out << ",\"active\":" << ((style & GWS_PUSH_BUTTON) && (w->winGetStatus() & WIN_STATUS_CHECK_LIKE) && GadgetCheckLikeButtonIsChecked(w) ? "true" : "false");
                if (auxiliary) out << ",\"auxiliary\":true,\"window\":" << quoted(windowName(w));
                if (occupant) out << ",\"occupant\":" << occupant->getID() << ",\"veterancy\":" << occupant->getVeterancyLevel();
                if (cmd && cmd->getCommandType() == GUI_COMMAND_EXIT_CONTAINER) out << ",\"passengerSlot\":true";
                if (entry) out << ",\"progress\":" << entry->getPercentComplete() / 100.f;
                if (cmd && TheGameText) {
                    out << ",\"description\":" << quoted(science != SCIENCE_INVALID ? utf8(scienceDescription) :
                        cmd->getDescriptionLabel().isEmpty() ? "" : utf8(TheGameText->fetch(cmd->getDescriptionLabel())));
                    Player *local = ThePlayerList ? ThePlayerList->getLocalPlayer() : nullptr;
                    if (science != SCIENCE_INVALID) out << ",\"science\":true,\"scienceCost\":" << TheScienceStore->getSciencePurchaseCost(science)
                        << ",\"learned\":" << (local && local->hasScience(science) ? "true" : "false");
                    const ThingTemplate *thing = cmd->getThingTemplate();
                    if (thing && local) out << ",\"cost\":" << thing->calcCostToBuild(local);
                    else if (cmd->getUpgradeTemplate() && local) out << ",\"cost\":" << cmd->getUpgradeTemplate()->calcCostToBuild(local);
                    const auto *power = cmd->getSpecialPowerTemplate();
                    auto *selected = TheInGameUI ? TheInGameUI->getFirstSelectedDrawable() : nullptr;
                    auto *object = selected ? selected->getObject() : nullptr;
                    if (power && local && (cmd->getCommandType() == GUI_COMMAND_SPECIAL_POWER_FROM_SHORTCUT ||
                        cmd->getCommandType() == GUI_COMMAND_SPECIAL_POWER_CONSTRUCT_FROM_SHORTCUT))
                        object = local->findMostReadyShortcutSpecialPowerOfType(power->getSpecialPowerType());
                    auto *module = power && object ? object->getSpecialPowerModule(power) : nullptr;
                    if (module) out << ",\"recharge\":" << module->getPercentReady();
                    out << ",\"confirm\":" << (cmd->getCommandType() == GUI_COMMAND_SELL ? "true" : "false");
                }
                out << '}';
            }
        }
        if (!(style & GWS_COMBO_BOX)) widgets(w->winGetChild(), out, first, depth + 1);
    }
}
void publish() {
    std::ostringstream out;
    // GeneralsX @bugfix Codex 14/10/2026 A live modal must get menu navigation even if it does not pause simulation.
    bool battle = battlefield() && (!TheWindowManager || !TheWindowManager->winGetModalWindow());
    out << "{\"ready\":true,\"battle\":" << (battle ? "true" : "false")
        << ",\"iconGeneration\":" << iconGeneration
        << ",\"aimX\":" << aimX << ",\"aimY\":" << aimY
        << ",\"width\":" << (TheDisplay ? TheDisplay->getWidth() : 1280)
        << ",\"height\":" << (TheDisplay ? TheDisplay->getHeight() : 720)
        << ",\"selected\":" << (TheInGameUI ? TheInGameUI->getSelectCount() : 0)
        << ",\"selecting\":" << (TheInGameUI && TheInGameUI->isSelecting() ? "true" : "false");
    Player *localPlayer = ThePlayerList ? ThePlayerList->getLocalPlayer() : nullptr;
    Player *p = ThePlayerList ? rts::getObservedOrLocalPlayer() : nullptr;
    if (p) out << ",\"money\":" << p->getMoney()->countMoney() << ",\"power\":"
        << p->getEnergy()->getProduction() - p->getEnergy()->getConsumption() << ",\"rank\":" << p->getRankLevel()
        << ",\"sciencePoints\":" << p->getSciencePurchasePoints()
        << ",\"faction\":" << quoted(p->getSide().str())
        << ",\"factionName\":" << quoted(p->getPlayerTemplate() ? utf8(p->getPlayerTemplate()->getDisplayName()) : p->getSide().str())
        << ",\"factionIcon\":" << quoted(imageKey(p->getPlayerTemplate() ? p->getPlayerTemplate()->getSideIconImage() : nullptr))
        << ",\"powerProduced\":" << p->getEnergy()->getProduction()
        << ",\"powerConsumed\":" << p->getEnergy()->getConsumption();
    if (p) {
        const int span = p->getSkillPointsLevelUp() - p->getSkillPointsLevelDown();
        out << ",\"rankProgress\":" << (span > 0 ? float(p->getSkillPoints() - p->getSkillPointsLevelDown()) / span : 1.f);
    }
    auto *d = TheInGameUI ? TheInGameUI->getFirstSelectedDrawable() : nullptr;
    if (TheControlBar && visible(TheControlBar->getContextParent(CP_OBSERVER_INFO))) {
        auto *observed = TheControlBar->getObserverLookAtPlayer();
        if (observed) out << ",\"observerName\":" << quoted(utf8(observed->getPlayerDisplayName()));
        const char *names[] = {"ControlBar.wnd:StaticTextNumberOfUnits", "ControlBar.wnd:StaticTextNumberOfBuildings", "ControlBar.wnd:StaticTextNumberOfUnitsKilled", "ControlBar.wnd:StaticTextNumberOfUnitsLost"};
        out << ",\"observerStats\":[";
        for (int i = 0; i < 4; ++i) {
            if (i) out << ',';
            auto *window = TheWindowManager->winGetWindowFromId(nullptr, TheNameKeyGenerator->nameToKey(names[i]));
            out << quoted(window ? utf8(window->winGetText()) : "");
        }
        out << ']';
    }
    if (d && d->getObject()) {
        out << ",\"selection\":" << quoted(utf8(d->getObject()->getTemplate()->getDisplayName()));
        const auto *thing = d->getObject()->getTemplate();
        out << ",\"portrait\":" << quoted(imageKey(thing->getSelectedPortraitImage() ? thing->getSelectedPortraitImage() : thing->getButtonImage()));
        auto *body = d->getObject()->getBodyModule();
        if (body) out << ",\"health\":" << body->getHealth() << ",\"maxHealth\":" << body->getMaxHealth();
        // GeneralsX @feature Codex 14/10/2026 Keep construction, reinforcement timers and passenger information when the original HUD moves off screen.
        auto *object = d->getObject();
        if (object->testStatus(OBJECT_STATUS_UNDER_CONSTRUCTION)) out << ",\"constructionProgress\":" << object->getConstructionPercent() / 100.f;
        if (auto *timer = static_cast<OCLUpdate *>(object->findUpdateModule(NAMEKEY("OCLUpdate"))))
            out << ",\"timerSeconds\":" << timer->getRemainingFrames() / LOGICFRAMES_PER_SECOND << ",\"timerProgress\":" << timer->getCountdownPercent();
        if (auto *contain = object->getContain()) if (contain->isDisplayedOnControlBar()) out << ",\"passengers\":" << contain->getContainCount() << ",\"passengerCapacity\":" << contain->getContainMax();
        out << ",\"veterancy\":" << object->getVeterancyLevel();
        ICoord2D point;
        if (TheTacticalView && TheTacticalView->worldToScreen(d->getPosition(), &point) && TheDisplay)
            out << ",\"selectionScreenX\":" << float(point.x) / TheDisplay->getWidth() << ",\"selectionScreenY\":" << float(point.y) / TheDisplay->getHeight();
    }
    // GeneralsX @test Codex 14/10/2026 Immutable selection members help distinguish a transient count from a committed control group.
    out << ",\"selectionMembers\":[";
    bool firstMember = true;
    if (TheInGameUI) for (auto *member : *TheInGameUI->getAllSelectedDrawables()) {
        if (!member || !member->getObject()) continue;
        if (!firstMember) out << ','; firstMember = false;
        out << "{\"id\":" << member->getObject()->getID() << ",\"name\":" << quoted(utf8(member->getObject()->getTemplate()->getDisplayName()))
            << ",\"selected\":" << (member->isSelected() ? "true" : "false") << '}';
    }
    out << "]";
    out << ",\"groups\":[";
    for (int index = 0; index < 10; ++index) {
        if (index) out << ',';
        auto *group = localPlayer ? localPlayer->getHotkeySquad(index) : nullptr;
        out << (group ? group->getLiveObjects().size() : 0);
    }
    out << ']';
    auto *deckRadar = dynamic_cast<W3DRadar *>(TheRadar);
    std::vector<W3DRadar::DeckMarker> markers;
    if (inMatch() && deckRadar) deckRadar->copyDeckMarkers(markers, viewportExpanded && battlefield());
    out << ",\"radarMarkers\":[";
    for (size_t i = 0; i < markers.size(); ++i) {
        if (i) out << ',';
        const auto &marker = markers[i];
        out << "{\"hero\":" << (marker.hero ? "true" : "false") << ",\"color\":" << marker.color << ",\"points\":[";
        for (int j = 0; j < (marker.hero ? 2 : 6); ++j) { if (j) out << ','; out << marker.points[j]; }
        out << "]}";
    }
    out << ']';
    std::vector<float> corners;
    if (inMatch() && deckRadar && deckRadar->copyDeckViewBox(corners)) {
        out << ",\"radarView\":[";
        for (size_t i = 0; i < corners.size(); ++i) { if (i) out << ','; out << corners[i]; }
        out << ']';
    }
    std::vector<float> radarBounds;
    if (inMatch() && deckRadar && deckRadar->copyDeckBounds(radarBounds)) {
        out << ",\"radarBounds\":[";
        for (size_t i = 0; i < radarBounds.size(); ++i) { if (i) out << ','; out << radarBounds[i]; }
        out << ']';
        out << ",\"radarWorldWidth\":" << deckRadar->getDeckWorldWidth()
            << ",\"radarWorldHeight\":" << deckRadar->getDeckWorldHeight();
    }
    ICoord2D boxStart;
    if (boxHeld && TheTacticalView && TheTacticalView->worldToScreen(&boxAnchor, &boxStart))
        out << ",\"boxX\":" << boxStart.x << ",\"boxY\":" << boxStart.y;
    out << ",\"targeting\":" << (TouchInput::hasArmedCommand() ? "true" : "false")
        << ",\"placing\":" << (TheInGameUI && TheInGameUI->getPendingPlaceType() ? "true" : "false");
    const auto *placing = TheInGameUI ? TheInGameUI->getPendingPlaceType() : nullptr;
    out << ",\"placementStage\":" << (placing ? TouchInput::placementRotating() ? 2 : 1 : 0);
    if (placing) out << ",\"placementName\":" << quoted(utf8(placing->getDisplayName()))
        << ",\"placementAngle\":" << TheInGameUI->getPlacementAngle();
    Coord3D placementSite;
    if (placing && TheInGameUI->getInputPlacementPosition(&placementSite))
        out << ",\"placementX\":" << placementSite.x << ",\"placementY\":" << placementSite.y;
    if (TheTacticalView) {
        Coord2D position = TheTacticalView->getPosition2D();
        out << ",\"cameraX\":" << position.x << ",\"cameraY\":" << position.y
            << ",\"cameraAngle\":" << TheTacticalView->getAngle() << ",\"cameraHeight\":" << TheTacticalView->getHeightAboveGround();
    }
    // GeneralsX @feature Codex 14/10/2026 Expose retail keyboard-only tactical actions without changing controller button bindings.
    struct Tool { const char *name, *description; bool selection; };
    static const Tool tools[] = {
        {"Last alert", "Jump to the game's last radar event, including attack and beacon alerts.", false},
        {"Select army", "Select combat units using the original select-all rules. Builders and harvesters are excluded.", false},
        {"Select aircraft", "Select your aircraft using the original select-all-aircraft command.", false},
        {"Select hero", "Select your faction's hero if one is available.", false},
        {"Select matching", "Select units matching your current selection using the game's original rules.", true},
        {"Scatter", "Order your selected units to scatter.", true},
        {"Create formation", "Arrange the selected group into a formation.", true},
        {"Reset camera", "Restore the game's default camera angle and zoom.", false},
        {"Next builder", "Cycle through builders, including those currently working. D-pad Up still finds an idle builder.", false}
    };
    out << ",\"tools\":[";
    Coord3D eventLocation;
    const bool hasEvent = TheRadar && TheRadar->getLastEventLoc(&eventLocation);
    for (int i = 0; i < int(sizeof(tools) / sizeof(tools[0])); ++i) {
        if (i) out << ',';
        const bool usable = inMatch() && (i != 0 || hasEvent) && (!tools[i].selection || TouchInput::hasControllableSelection());
        out << "{\"id\":" << -101 - i << ",\"label\":" << quoted(tools[i].name) << ",\"description\":" << quoted(tools[i].description)
            << ",\"intent\":18,\"value\":" << i << ",\"enabled\":" << (usable ? "true" : "false") << ",\"identity\":" << quoted("tool:" + std::to_string(i)) << '}';
    }
    out << ']';
    out << ",\"widgets\":[";
    bool first = true;
    if (TheWindowManager) {
        auto *modal = TheWindowManager->winGetModalWindow();
        // GeneralsX @bugfix Codex 14/10/2026 The original quit/options layouts are not on the modal stack.
        if (!modal && inMatch() && TheInGameUI && TheInGameUI->isQuitMenuVisible()) {
            modal = menuRoot("PopupSaveLoad.wnd:ButtonBack");
            if (!modal) modal = menuRoot("OptionsMenu.wnd:ButtonBack");
            if (!modal) modal = menuRoot("QuitMenu.wnd:ButtonReturn");
        }
        widgets(modal ? modal : TheWindowManager->winGetWindowList(), out, first, 0, modal == nullptr);
    }
    out << "]}";
    std::vector<unsigned int> nextRadar;
    auto *radar = dynamic_cast<W3DRadar *>(TheRadar);
    const Uint64 now = SDL_GetTicks();
    const bool updateRadar = now - lastRadar >= 100 || !inMatch() || !rts::localPlayerHasRadar();
    if (updateRadar) {
        if (inMatch() && radar) radar->copyDeckRadar(nextRadar);
        lastRadar = now;
    }
    std::lock_guard<std::mutex> guard(bridgeMutex);
    published = out.str();
    if (updateRadar) radarPixels.swap(nextRadar);
}
}

extern "C" JNIEXPORT void JNICALL Java_com_generalsx_zerohour_ThorBridge_nativeAxes(JNIEnv *, jclass, jfloat lx, jfloat ly, jfloat rx, jfloat ry, jboolean precise, jboolean queue) {
    std::lock_guard<std::mutex> guard(bridgeMutex);
    stick[0] = lx; stick[1] = ly; stick[2] = rx; stick[3] = ry;
    precision = precise; waypoint = queue; enabled = axesEnabled = true;
    suspended = false;
}
extern "C" JNIEXPORT void JNICALL Java_com_generalsx_zerohour_ThorBridge_nativeIntent(JNIEnv *, jclass, jint type, jint value, jfloat x, jfloat y) {
    std::lock_guard<std::mutex> guard(bridgeMutex);
    enabled = true;
    if (type == 96) { enabled = axesEnabled = false; std::fill(stick, stick + 4, 0); return; }
    if (type == 99) { pending.clear(); pending.push_back({97, 0, 0, 0}); std::fill(stick, stick + 4, 0); axesEnabled = false; pendingText.clear(); textReady = false; suspended = true; return; }
    suspended = false;
    // GeneralsX @bugfix Codex 14/10/2026 Coalesce drag samples without dropping orders between them.
    if (type == 15 && value == 0 && !pending.empty() && pending.back().type == 15 && pending.back().value == 0) {
        pending.back().x = x; pending.back().y = y; return;
    }
    if (pending.size() < 128) pending.push_back({type, value, x, y});
}
// GeneralsX @bugfix Codex 14/10/2026 A stale snapshot cannot activate a different command in a reused slot.
extern "C" JNIEXPORT void JNICALL Java_com_generalsx_zerohour_ThorBridge_nativeActivate(JNIEnv *env, jclass, jint id, jstring action) {
    const char *chars = action ? env->GetStringUTFChars(action, nullptr) : nullptr;
    std::string expected = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(action, chars);
    std::lock_guard<std::mutex> guard(bridgeMutex);
    enabled = true;
    suspended = false;
    if (pending.size() < 128) pending.push_back({6, id, -1, -1, expected});
}
// GeneralsX @bugfix Codex 14/10/2026 The controller validates the selected object and queue/passenger target as well as the command name.
extern "C" JNIEXPORT void JNICALL Java_com_generalsx_zerohour_ThorBridge_nativeActivateChecked(JNIEnv *env, jclass, jint id, jstring action, jstring activation) {
    auto copy = [env](jstring value) {
        const char *chars = value ? env->GetStringUTFChars(value, nullptr) : nullptr;
        std::string result = chars ? chars : "";
        if (chars) env->ReleaseStringUTFChars(value, chars);
        return result;
    };
    const auto expectedAction = copy(action), expectedActivation = copy(activation);
    std::lock_guard<std::mutex> guard(bridgeMutex);
    enabled = true; suspended = false;
    if (pending.size() < 128) pending.push_back({6, id, -1, -1, expectedAction, expectedActivation});
}
extern "C" JNIEXPORT jstring JNICALL Java_com_generalsx_zerohour_ThorBridge_nativeSnapshot(JNIEnv *env, jclass) {
    std::lock_guard<std::mutex> guard(bridgeMutex);
    return env->NewStringUTF(published.c_str());
}
extern "C" JNIEXPORT jintArray JNICALL Java_com_generalsx_zerohour_ThorBridge_nativeRadar(JNIEnv *env, jclass) {
    std::lock_guard<std::mutex> guard(bridgeMutex);
    if (radarPixels.empty()) return nullptr;
    jintArray result = env->NewIntArray(static_cast<jsize>(radarPixels.size()));
    if (result) env->SetIntArrayRegion(result, 0, static_cast<jsize>(radarPixels.size()), reinterpret_cast<const jint *>(radarPixels.data()));
    return result;
}
// GeneralsX @feature Codex 14/10/2026 JNI only copies completed artwork or queues a bounded request.
extern "C" JNIEXPORT jintArray JNICALL Java_com_generalsx_zerohour_ThorBridge_nativeIcon(JNIEnv *env, jclass, jstring name) {
    if (!name) return nullptr;
    const char *chars = env->GetStringUTFChars(name, nullptr);
    if (!chars) return nullptr;
    std::string key(chars); env->ReleaseStringUTFChars(name, chars);
    if (key.empty() || key.size() > 256) return nullptr;
    std::lock_guard<std::mutex> guard(bridgeMutex);
    auto found = iconCopies.find(key);
    if (found == iconCopies.end()) {
        if (iconRequests.size() < 128 && requestedIcons.insert(key).second) iconRequests.push_back(key);
        return nullptr;
    }
    found->second.used = SDL_GetTicks();
    jintArray result = env->NewIntArray(static_cast<jsize>(found->second.pixels.size()));
    if (result && !found->second.pixels.empty()) env->SetIntArrayRegion(result, 0, static_cast<jsize>(found->second.pixels.size()), reinterpret_cast<const jint *>(found->second.pixels.data()));
    return result;
}
extern "C" JNIEXPORT void JNICALL Java_com_generalsx_zerohour_ThorBridge_nativeText(JNIEnv *env, jclass, jstring value) {
    if (!value) return;
    const jchar *chars = env->GetStringChars(value, nullptr);
    if (!chars) return;
    std::lock_guard<std::mutex> guard(bridgeMutex);
    pendingText.assign(chars, chars + env->GetStringLength(value)); pendingText.push_back(0); textReady = true;
    env->ReleaseStringChars(value, chars);
}
extern "C" JNIEXPORT void JNICALL Java_com_generalsx_zerohour_ThorBridge_nativeOptions(JNIEnv *, jclass, jboolean snap, jboolean deck) {
    std::lock_guard<std::mutex> guard(bridgeMutex); snapSetting = snap; externalDeckSetting = deck;
}
namespace ThorInput {
void reset() {
    std::lock_guard<std::mutex> guard(bridgeMutex);
    pending.clear(); std::fill(stick, stick + 4, 0); precision = waypoint = axesEnabled = false;
    aimX = aimY = .5f; lastPump = 0; boxHeld = false; textReady = false; radarPixels.clear(); editorId = 0;
    smoothPan[0] = smoothPan[1] = 0;
    TouchInput::placementReset();
    iconCopies.clear(); pageCopies.clear(); windowNames.clear(); iconRequests.clear(); requestedIcons.clear(); ++iconGeneration;
    published = "{\"ready\":false}";
}
void pump() {
    pumpIcons();
    std::deque<Intent> work; float axes[4]; bool precise, queue, active, snap, externalDeck, useAxes;
    std::vector<WideChar> text;
    { std::lock_guard<std::mutex> guard(bridgeMutex); work.swap(pending); std::copy(stick, stick + 4, axes); precise = precision; queue = waypoint; active = enabled && !suspended; useAxes = axesEnabled; snap = snapSetting; externalDeck = externalDeckSetting; if (textReady) { text.swap(pendingText); textReady = false; } }
    if (TheControlBar && TheTacticalView && TheDisplay) {
        const bool expand = externalDeck && inMatch();
        TheControlBar->presentOnExternalDeck(expand);
        // GeneralsX @bugfix Codex 14/10/2026 The original floating group panel duplicates the Thor's ten-group wheel.
        if (TheWindowManager && TheNameKeyGenerator) {
            auto *groupPanel = TheWindowManager->winGetWindowFromId(nullptr, TheNameKeyGenerator->nameToKey("GroupPanel.wnd:ButtonHandle"));
            if (groupPanel) { while (groupPanel->winGetParent()) groupPanel = groupPanel->winGetParent(); groupPanel->winSetPresentedHere(!expand); }
        }
        if (expand) TheControlBar->setFullViewportHeight();
        else if (viewportExpanded) TheControlBar->setScaledViewportHeight();
        viewportExpanded = expand;
    }
    if (!active) { smoothPan[0] = smoothPan[1] = 0; }
    for (const auto &i : work) if (i.type == 97) { boxHeld = false; if (TheInGameUI) TheInGameUI->clearTouchAimPoint(); }
    if (active && !text.empty() && TheWindowManager) {
        auto *field = editorId ? TheWindowManager->winGetWindowFromId(nullptr, editorId) : TheWindowManager->winGetFocus();
        if (field && (field->winGetStyle() & GWS_ENTRY_FIELD)) {
            UnicodeString value; value.set(text.data()); GadgetTextEntrySetText(field, value);
            TheWindowManager->winSendSystemMsg(field->winGetOwner(), GEM_EDIT_DONE, reinterpret_cast<WindowMsgData>(field), 0);
        }
    }
    Uint64 now = SDL_GetTicks();
    float dt = lastPump ? std::min(.05f, float(now - lastPump) / 1000.f) : 0.f;
    lastPump = now;
    const int width = TheDisplay ? TheDisplay->getWidth() : 1280;
    const int height = TheDisplay ? TheDisplay->getHeight() : 720;
    if (active && useAxes && battlefield() && TheTacticalView && TheInGameUI) {
        const bool rotating = TouchInput::placementRotating();
        if (precise && !rotating) { aimX = std::clamp(aimX + axes[2] * dt * .35f, .05f, .95f); aimY = std::clamp(aimY + axes[3] * dt * .35f, .05f, externalDeck ? .95f : .72f); }
        else {
            aimX = .5f; aimY = externalDeck ? .5f : .4f;
            TheTacticalView->userZoom(-axes[3] * dt * 250.f);
            TheTacticalView->userSetAngle(TheTacticalView->getAngle() + axes[2] * dt * 1.25f);
        }
        // GeneralsX @bugfix Codex 14/10/2026 scrollBy already converts screen axes to world space.
        // Use the original keyboard scroll scale once, with a short analog response ramp.
        if (rotating) {
            smoothPan[0] = smoothPan[1] = 0;
            TouchInput::placementDirection(axes[0], axes[1]);
        } else {
            const float blend = 1.f - std::exp(-dt / .055f);
            for (int index = 0; index < 2; ++index) {
                smoothPan[index] += (axes[index] - smoothPan[index]) * blend;
                if (axes[index] == 0 && std::abs(smoothPan[index]) < .015f) smoothPan[index] = 0;
            }
            const float speed = 200.f * 30.f * dt * (TheGlobalData ? TheGlobalData->m_keyboardScrollFactor : .5f);
            Coord2D delta{smoothPan[0] * speed, smoothPan[1] * speed};
            TheTacticalView->userScrollBy(&delta);
        }
        if (snap && !precise && !boxHeld && !TouchInput::hasArmedCommand() && !TheInGameUI->getPendingPlaceType() && TheGameClient) {
            int targetX = int(aimX * width), targetY = int(aimY * height);
            int best = int(width * .022f) * int(width * .022f);
            Drawable *hover = nullptr;
            for (auto *d = TheGameClient->getDrawableList(); d; d = d->getNextDrawable()) {
                auto *o = d->getObject(); ICoord2D point;
                if (!o || !CanSelectDrawable(d, FALSE) || !TheTacticalView->worldToScreen(d->getPosition(), &point)) continue;
                int dx = point.x - targetX, dy = point.y - targetY, distance = dx * dx + dy * dy;
                if (distance < best) { best = distance; hover = d; aimX = float(point.x) / width; aimY = float(point.y) / height; }
            }
            if (hover) TheInGameUI->setTouchHoverDrawable(hover->getID());
        }
        if (TouchInput::hasArmedCommand()) { TouchInput::beginAiming(int(aimX * width), int(aimY * height)); TheInGameUI->setTouchAimPoint(int(aimX * width), int(aimY * height), TouchInput::armedTargetValid(int(aimX * width), int(aimY * height))); }
        else if (TheInGameUI->getPendingPlaceType()) TheInGameUI->setTouchAimPoint(int(aimX * width), int(aimY * height), TRUE);
    }
    if (active) for (const auto &i : work) {
        if (i.type == 97) { boxHeld = false; if (TheInGameUI) TheInGameUI->clearTouchAimPoint(); continue; }
        const int x = int((i.x < 0 ? aimX : i.x) * width), y = int((i.y < 0 ? aimY : i.y) * height);
        if (i.type == 6) { activate(i.value, i.action, i.activation); continue; }
        if (i.type == 11) {
            if (TheDisplay && TheDisplay->isMoviePlaying()) TouchInput::tap(0, 0);
            else if (inMatch()) message(GameMessage::MSG_META_OPTIONS);
            continue;
        }
        if (i.type == 12) {
            auto *field = TheWindowManager ? (editorId ? TheWindowManager->winGetWindowFromId(nullptr, editorId) : TheWindowManager->winGetFocus()) : nullptr;
            if (field && (field->winGetStyle() & GWS_COMBO_BOX)) {
                if (i.value == SDL_SCANCODE_RETURN) GadgetComboBoxHideList(field);
                else {
                    Int pos = 0; GadgetComboBoxGetSelectedPos(field, &pos);
                    const Int length = GadgetComboBoxGetLength(field);
                    if (length > 0) GadgetComboBoxSetSelectedPos(field, std::clamp(pos + (i.value == SDL_SCANCODE_UP || i.value == SDL_SCANCODE_LEFT ? -1 : 1), 0, length - 1));
                }
            } else key(static_cast<SDL_Scancode>(i.value));
            if (i.value == SDL_SCANCODE_RETURN) editorId = 0;
            continue;
        }
        if (!battlefield() || !TheInGameUI) continue;
        TheInGameUI->setWaypointMode(queue);
        switch (i.type) {
        case 0:
            if (TheInGameUI->getPendingPlaceType()) TouchInput::placementStep(x,y);
            else if (TouchInput::hasArmedCommand()) click(x, y);
            else if (i.value == 2) TouchInput::doubleTap(x, y); else TouchInput::tap(x, y);
            break;
        case 1:
            if (TheInGameUI->getPendingPlaceType()) TouchInput::placementCancel();
            else TouchInput::cancelOrDeselect();
            break;
        case 2: {
            ICoord2D point{x, y}; Coord3D world;
            if (TouchInput::hasControllableSelection() && TheTacticalView->screenToTerrain(&point, &world)) {
                TheInGameUI->clearAttackMoveToMode(); TheInGameUI->toggleAttackMoveToMode();
                TheGameClient->evaluateContextCommand(nullptr, &world, CommandTranslator::DO_COMMAND);
                TheInGameUI->clearAttackMoveToMode();
            }
            break;
        }
        case 3: message(GameMessage::MSG_DO_STOP); break;
        case 4: center(); break;
        case 5: cycle(i.value); break;
        case 7: if (i.value >= 0 && i.value < 10) message(static_cast<GameMessage::Type>(GameMessage::MSG_META_SELECT_TEAM0 + i.value)); break;
        case 8: if (i.value >= 0 && i.value < 10) message(static_cast<GameMessage::Type>(GameMessage::MSG_META_CREATE_TEAM0 + i.value)); break;
        case 9: {
            // GeneralsX @bugfix Codex 14/10/2026 Merge selection into the saved group through the original command.
            if (i.value < 0 || i.value >= 10 || !ThePlayerList) break;
            auto *player = ThePlayerList->getLocalPlayer();
            auto *squad = player ? player->getHotkeySquad(i.value) : nullptr;
            auto *m = TheMessageStream->appendMessage(static_cast<GameMessage::Type>(GameMessage::MSG_CREATE_TEAM0 + i.value));
            std::vector<ObjectID> ids;
            if (squad) for (auto *o : squad->getLiveObjects()) if (o->isLocallyControlled()) ids.push_back(o->getID());
            for (auto *d = TheGameClient->getDrawableList(); d; d = d->getNextDrawable())
                if (d->isSelected() && d->getObject() && d->getObject()->isLocallyControlled()) ids.push_back(d->getObject()->getID());
            std::sort(ids.begin(), ids.end()); ids.erase(std::unique(ids.begin(), ids.end()), ids.end());
            for (auto id : ids) m->appendObjectIDArgument(id);
            break;
        }
        case 10: if (i.value >= 0 && i.value < 10) message(static_cast<GameMessage::Type>(GameMessage::MSG_META_VIEW_TEAM0 + i.value)); break;
        case 13: {
            if (TheInGameUI->getPendingPlaceType()) { boxHeld = false; break; }
            ICoord2D pixel{x, y}; boxHeld = TheTacticalView && TheTacticalView->screenToTerrain(&pixel, &boxAnchor);
            boxDown = now; break;
        }
        case 14: {
            if (TheInGameUI->getPendingPlaceType()) { boxHeld = false; TouchInput::placementStep(x,y); break; }
            if (!boxHeld) break;
            boxHeld = false;
            ICoord2D start;
            if (now - boxDown >= 250 && !TouchInput::hasArmedCommand() && !TheInGameUI->getPendingPlaceType() && !TheTacticalView->worldToScreen(&boxAnchor, &start)) break;
            if (TouchInput::hasArmedCommand() || TheInGameUI->getPendingPlaceType() || now - boxDown < 250 || !TheTacticalView->worldToScreen(&boxAnchor, &start) || (std::abs(start.x - x) + std::abs(start.y - y) < 6)) {
                if (TheInGameUI->getPendingPlaceType()) {
                    ICoord2D point{x, y};
                    if (!TheInGameUI->isPlacementAnchored()) TheInGameUI->setPlacementStart(&point);
                    TheInGameUI->setPlacementEnd(&point); click(x, y);
                } else if (TouchInput::hasArmedCommand()) click(x, y);
                else if (queue) {
                    ICoord2D point{x, y}; auto *d = TheTacticalView->pickDrawable(&point, FALSE, PICK_TYPE_SELECTABLE);
                    auto *o = d ? d->getObject() : nullptr;
                    if (o && o->isLocallyControlled() && CanSelectDrawable(d, FALSE)) {
                        TheInGameUI->selectDrawable(d);
                        auto *m = TheMessageStream->appendMessage(GameMessage::MSG_CREATE_SELECTED_GROUP);
                        m->appendBooleanArgument(FALSE); m->appendObjectIDArgument(o->getID());
                    } else TouchInput::tap(x, y);
                } else if (i.value == 2) TouchInput::doubleTap(x, y); else TouchInput::tap(x, y);
                break;
            }
            if (!queue) TheInGameUI->deselectAllDrawables(false);
            auto *m = TheMessageStream->appendMessage(GameMessage::MSG_CREATE_SELECTED_GROUP);
            m->appendBooleanArgument(queue ? FALSE : TRUE);
            for (auto *d = TheGameClient->getDrawableList(); d; d = d->getNextDrawable()) {
                auto *o = d->getObject(); ICoord2D point;
                if (!o || !o->isLocallyControlled() || !CanSelectDrawable(d, FALSE) || !d->isMassSelectable() || !TheTacticalView->worldToScreen(d->getPosition(), &point)) continue;
                if (point.x >= std::min(x, start.x) && point.x <= std::max(x, start.x) && point.y >= std::min(y, start.y) && point.y <= std::max(y, start.y)) {
                    TheInGameUI->selectDrawable(d); m->appendObjectIDArgument(o->getID());
                }
            }
            break;
        }
        case 15: {
            auto *radar = dynamic_cast<W3DRadar *>(TheRadar);
            if (!radar || !rts::localPlayerHasRadar()) break;
            Coord3D world;
            if (!radar->deckPointToWorld(i.x, i.y, &world)) break;
            if (i.value == 0) TheTacticalView->userLookAt(&world);
            else if (TouchInput::hasControllableSelection()) TheGameClient->evaluateContextCommand(nullptr, &world, CommandTranslator::DO_COMMAND);
            break;
        }
        case 16: if (TheInGameUI->getPendingPlaceType()) TouchInput::placementCancel(); break;
        case 17: if (TheInGameUI->getPendingPlaceType()) TouchInput::placementStep(i.x < 0 ? -1 : x,i.y < 0 ? -1 : y); break;
        case 18: {
            static const GameMessage::Type commands[] = {GameMessage::MSG_META_VIEW_LAST_RADAR_EVENT, GameMessage::MSG_META_SELECT_ALL,
                GameMessage::MSG_META_SELECT_ALL_AIRCRAFT, GameMessage::MSG_META_SELECT_HERO, GameMessage::MSG_META_SELECT_MATCHING_UNITS,
                GameMessage::MSG_META_SCATTER, GameMessage::MSG_META_CREATE_FORMATION, GameMessage::MSG_META_CAMERA_RESET, GameMessage::MSG_META_SELECT_NEXT_WORKER};
            if (i.value >= 0 && i.value < int(sizeof(commands) / sizeof(commands[0]))) message(commands[i.value]);
            break;
        }
        // GeneralsX @feature Codex 14/10/2026 LT+L3 matches the current selection on screen only; repeated use cannot expand across the map.
        case 19:
            if (!TheInGameUI->getPendingPlaceType() && !TouchInput::hasArmedCommand()) TheInGameUI->selectMatchingAcrossScreen();
            break;
        }
        TheInGameUI->setWaypointMode(FALSE);
    }
    TouchInput::placementPump();
    if (now - lastSnapshot >= 33) { publish(); lastSnapshot = now; }
}
}
#else
namespace ThorInput { void pump() {} void reset() {} }
#endif
