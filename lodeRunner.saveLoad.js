/**
 * Save/load support for the current in-progress game.
 *
 * This feature intentionally uses dedicated localStorage slots so it does not
 * interfere with the game's existing progress, high-score, custom-level backup,
 * or restore mechanisms.
 */

var GAME_STATE_SAVE_VERSION = 1;

function hasSavedGameState()
{
	return getSavedGameStateEntries().length > 0;
}

function canSaveGameState()
{
	if(playMode != PLAY_CLASSIC && playMode != PLAY_MODERN) return false;
	if(changingLevel || !map || !runner) return false;
	if(gameState == GAME_START || gameState == GAME_RUNNING) return true;
	if(gameState == GAME_PAUSE && (lastGameState == GAME_START || lastGameState == GAME_RUNNING)) return true;
	return false;
}

function saveGameStateMenu(id, callbackFun)
{
	var saved = saveCurrentGameStateToNewSlot(0);
	if(callbackFun) callbackFun();
	setTimeout(function() {
		showTipsText(saved ? "GAME STATE SAVED" : "CANNOT SAVE NOW", 2500);
	}, 50);
}

function loadGameStateMenu(id, callbackFun)
{
	if(callbackFun) callbackFun();
	showLoadGameStateMenu();
}

function deleteGameStateMenu(id, callbackFun)
{
	if(callbackFun) callbackFun();
	showDeleteGameStateMenu();
}

function saveCurrentGameState(showMsg)
{
	return saveCurrentGameStateToNewSlot(showMsg);
}

function loadCurrentGameState(showMsg)
{
	var saves = getSavedGameStateEntries();
	if(saves.length <= 0) {
		if(showMsg) showTipsText("NO SAVED GAME", 2500);
		return false;
	}
	return loadGameStateById(saves[0].id, showMsg);
}

function saveCurrentGameStateToNewSlot(showMsg)
{
	if(!canSaveGameState()) {
		if(showMsg) showTipsText("CANNOT SAVE NOW", 2500);
		return false;
	}

	try {
		var snapshot = buildGameStateSnapshot();
		var entry = createGameStateSaveEntry(snapshot);
		var saves = getSavedGameStateEntries();

		saves.unshift(entry);
		if(saves.length > MAX_GAME_STATE_SAVES) saves = saves.slice(0, MAX_GAME_STATE_SAVES);

		setSavedGameStateEntries(saves);
		if(showMsg) showTipsText("GAME STATE SAVED", 2500);
		return true;
	} catch(e) {
		error("saveCurrentGameStateToNewSlot failed: " + e.message);
		if(showMsg) showTipsText("SAVE FAILED", 2500);
		return false;
	}
}

function showLoadGameStateMenu()
{
	var saves = getSavedGameStateEntries();
	if(saves.length <= 0) {
		setTimeout(function() { showTipsText("NO SAVED GAME", 2500); }, 50);
		return false;
	}

	var menuList = buildSavedGameMenuList(saves, loadSelectedGameStateMenu, null);
	menuDialog(" Load Saved Game ", menuList, mainStage, tileScale, 1, null, saves);
	return true;
}

function showDeleteGameStateMenu()
{
	var saves = getSavedGameStateEntries();
	if(saves.length <= 0) {
		setTimeout(function() { showTipsText("NO SAVED GAME", 2500); }, 50);
		return false;
	}

	var menuList = buildSavedGameMenuList(saves, confirmDeleteSelectedGameStateMenu, null);
	menuDialog(" Delete Saved Game ", menuList, mainStage, tileScale, 1, null, saves);
	return true;
}

function buildSavedGameMenuList(saves, activeFun, backFun)
{
	var menuList = [{ activeItem: 0 }];
	for(var i = 0; i < saves.length; i++) {
		menuList.push({ name: " " + buildSaveMenuLabel(saves[i], i) + " ", activeFun: activeFun });
	}
	menuList.push({ name: " Back ", activeFun: backFun });
	return menuList;
}

function buildSaveMenuLabel(entry, idx)
{
	var modeName = entry.mode == PLAY_MODERN ? "Training" : "Challenge";
	var levelNo = ("00" + safeNumber(entry.level, 1)).slice(-3);
	var scoreNo = safeNumber(entry.score, 0);
	return (idx + 1) + ". " + modeName + " L" + levelNo + " S" + scoreNo + " " + formatSaveDate(entry.updatedAt || entry.createdAt);
}

function loadSelectedGameStateMenu(id, saves)
{
	if(!saves || id >= saves.length) return;
	loadGameStateById(saves[id].id, 1);
}

function confirmDeleteSelectedGameStateMenu(id, saves)
{
	if(!saves || id >= saves.length) return;
	var saveEntry = saves[id];
	var msg = ["Delete saved game ?", buildShortSaveDescription(saveEntry)];
	yesNoDialog(msg, yesBitmap, noBitmap, mainStage, tileScale, function(yes) {
		if(yes) deleteGameStateById(saveEntry.id, 1);
	});
}

function buildShortSaveDescription(entry)
{
	var modeName = entry.mode == PLAY_MODERN ? "Training" : "Challenge";
	return modeName + " Level " + safeNumber(entry.level, 1) + " - " + formatSaveDate(entry.updatedAt || entry.createdAt);
}

function loadGameStateById(saveId, showMsg)
{
	var entry = getSavedGameStateEntryById(saveId);
	if(!entry) {
		if(showMsg) showTipsText("SAVED GAME NOT FOUND", 2500);
		return false;
	}
	if(!isValidSaveEntry(entry) || !validateGameStateSnapshot(entry.state)) {
		if(showMsg) showTipsText("INVALID SAVE DATA", 2500);
		return false;
	}

	try {
		restoreGameStateSnapshot(entry.state);
		if(showMsg) showTipsText("GAME STATE LOADED", 2500);
		return true;
	} catch(e) {
		error("loadGameStateById failed: " + e.message);
		if(showMsg) showTipsText("LOAD FAILED", 2500);
		return false;
	}
}

function deleteGameStateById(saveId, showMsg)
{
	try {
		var saves = getSavedGameStateEntries();
		var nextSaves = [];
		var deleted = false;

		for(var i = 0; i < saves.length; i++) {
			if(saves[i].id == saveId) deleted = true;
			else nextSaves.push(saves[i]);
		}

		if(!deleted) {
			if(showMsg) showTipsText("SAVED GAME NOT FOUND", 2500);
			return false;
		}

		setSavedGameStateEntries(nextSaves);
		if(showMsg) showTipsText("SAVED GAME DELETED", 2500);
		return true;
	} catch(e) {
		error("deleteGameStateById failed: " + e.message);
		if(showMsg) showTipsText("DELETE FAILED", 2500);
		return false;
	}
}

function getSavedGameStateEntryById(saveId)
{
	var saves = getSavedGameStateEntries();
	for(var i = 0; i < saves.length; i++) {
		if(saves[i].id == saveId) return saves[i];
	}
	return null;
}

function getSavedGameStateEntries()
{
	migrateLegacySingleSaveIfNeeded();

	var infoJSON = getStorage(STORAGE_GAME_STATES);
	if(infoJSON == null) return [];

	try {
		var saves = JSON.parse(infoJSON);
		if(!saves || !saves.length) return [];

		var validSaves = [];
		for(var i = 0; i < saves.length; i++) {
			if(isValidSaveEntry(saves[i])) validSaves.push(saves[i]);
		}
		return validSaves;
	} catch(e) {
		error("getSavedGameStateEntries failed: " + e.message);
		return [];
	}
}

function setSavedGameStateEntries(saves)
{
	setStorage(STORAGE_GAME_STATES, JSON.stringify(saves || []));
}

function isValidSaveEntry(entry)
{
	return entry &&
		typeof entry.id == "string" &&
		entry.state &&
		typeof entry.createdAt == "number" &&
		typeof entry.updatedAt == "number";
}

function createGameStateSaveEntry(snapshot)
{
	var now = Date.now();
	return {
		id: createGameStateSaveId(now),
		label: buildSnapshotLabel(snapshot),
		createdAt: now,
		updatedAt: now,
		mode: snapshot.game.playMode,
		level: snapshot.game.curLevel,
		score: snapshot.game.curScore,
		lives: snapshot.game.runnerLife,
		state: snapshot
	};
}

function createGameStateSaveId(time)
{
	return "save-" + time + "-" + Math.floor(Math.random() * 1000000);
}

function buildSnapshotLabel(snapshot)
{
	var modeName = snapshot.game.playMode == PLAY_MODERN ? "Training" : "Challenge";
	return modeName + " - Level " + snapshot.game.curLevel + " - Score " + snapshot.game.curScore;
}

function formatSaveDate(time)
{
	var d = new Date(time);
	if(isNaN(d.getTime())) return "unknown";
	return d.getFullYear() + "-" +
		("0" + (d.getMonth() + 1)).slice(-2) + "-" +
		("0" + d.getDate()).slice(-2) + " " +
		("0" + d.getHours()).slice(-2) + ":" +
		("0" + d.getMinutes()).slice(-2);
}

function migrateLegacySingleSaveIfNeeded()
{
	var newSaveJSON = getStorage(STORAGE_GAME_STATES);
	if(newSaveJSON != null) return;

	var legacyJSON = getStorage(STORAGE_GAME_STATE);
	if(legacyJSON == null) return;

	try {
		var snapshot = JSON.parse(legacyJSON);
		if(!validateGameStateSnapshot(snapshot)) return;

		var migrated = createGameStateSaveEntry(snapshot);
		migrated.label = "Migrated Save";
		setSavedGameStateEntries([migrated]);
	} catch(e) {
		error("migrateLegacySingleSaveIfNeeded failed: " + e.message);
	}
}

function buildGameStateSnapshot()
{
	var restoredGameState = gameState;
	if(restoredGameState == GAME_PAUSE) restoredGameState = lastGameState;
	if(restoredGameState != GAME_START && restoredGameState != GAME_RUNNING) restoredGameState = GAME_START;

	return {
		v: GAME_STATE_SAVE_VERSION,
		savedAt: (new Date()).toISOString(),
		game: {
			playMode: playMode,
			playData: playData,
			curLevel: curLevel,
			maxLevel: maxLevel,
			passedLevel: passedLevel,
			runnerLife: runnerLife,
			curScore: curScore,
			curTime: curTime,
			curGetGold: curGetGold,
			curGuardDeadNo: curGuardDeadNo,
			goldCount: goldCount,
			goldComplete: goldComplete,
			sometimePlayInGodMode: sometimePlayInGodMode,
			playTickTimer: playTickTimer,
			gameState: restoredGameState,
			curAiVersion: curAiVersion,
			speed: speed
		},
		runner: packPlayerObj(runner),
		guards: packGuardObj(),
		map: packMapObj(),
		hole: packHoleObj(),
		fillHoles: packFillHoleObj(),
		queues: {
			shakingGuardList: packNumberArray(shakingGuardList),
			rebornGuardList: packNumberArray(rebornGuardList)
		}
	};
}

function validateGameStateSnapshot(snapshot)
{
	if(!snapshot || snapshot.v != GAME_STATE_SAVE_VERSION) return false;
	if(!snapshot.game || !snapshot.runner || !snapshot.map) return false;
	if(snapshot.game.playMode != PLAY_CLASSIC && snapshot.game.playMode != PLAY_MODERN) return false;
	if(snapshot.game.curLevel < 1) return false;
	if(!snapshot.map.length || snapshot.map.length != NO_OF_TILES_Y) return false;
	return true;
}

function restoreGameStateSnapshot(snapshot)
{
	soundStop(soundFall);
	soundStop(soundDig);
	stopPlayTicker();
	stopAllSpriteObj();
	disableAutoDemoTimer();

	gameState = GAME_WAITING;
	changingLevel = 1;

	playMode = snapshot.game.playMode;
	playData = snapshot.game.playData;
	playData2GameVersionMenuId();
	setLastPlayMode();

	if(playData == PLAY_DATA_USERDEF) {
		getEditLevelInfo();
		if(editLevels <= 0) throw new Error("Saved game uses custom levels, but no custom levels are available.");
		levelData = editLevelData;
	} else {
		levelData = getPlayVerData(playData);
	}

	curLevel = clampNumber(snapshot.game.curLevel, 1, levelData.length, 1);
	curAiVersion = snapshot.game.curAiVersion || AI_VERSION;
	initHotKeyVariable();

	showLevel(levelData[curLevel-1]);

	applyMapObj(snapshot.map);
	applyScalarGameState(snapshot.game);
	applyRunnerObj(snapshot.runner);
	applyGuardObj(snapshot.guards || []);
	applyHoleObj(snapshot.hole);
	applyFillHoleObj(snapshot.fillHoles || []);
	applyAnimationQueues(snapshot.queues || {});

	keyAction = ACT_STOP;
	changingLevel = 0;
	gameState = snapshot.game.gameState == GAME_RUNNING ? GAME_RUNNING : GAME_START;
	lastGameState = gameState;

	redrawGameInfo();
	moveSprite2Top();
	setGroundInfoOrder();
	menuIconEnable();
	startAllSpriteObj();
	startPlayTicker();
	mainStage.update();
}

function applyScalarGameState(game)
{
	maxLevel = game.maxLevel || curLevel;
	passedLevel = game.passedLevel || 0;
	runnerLife = game.runnerLife || RUNNER_LIFE;
	curScore = game.curScore || 0;
	curTime = game.curTime || 0;
	curGetGold = game.curGetGold || 0;
	curGuardDeadNo = game.curGuardDeadNo || 0;
	goldCount = game.goldCount || 0;
	goldComplete = game.goldComplete ? 1 : 0;
	sometimePlayInGodMode = game.sometimePlayInGodMode || 0;
	playTickTimer = game.playTickTimer || 0;
	if(typeof game.speed != "undefined") speed = clampNumber(game.speed, 0, speedMode.length-1, speed);
}

function redrawGameInfo()
{
	if(playMode == PLAY_CLASSIC) {
		drawScore(0);
		drawLife();
	} else {
		drawGold(0);
		drawGuard(0);
		drawTime(0);
	}
	drawLevel();
}

function packPlayerObj(obj)
{
	return {
		pos: packPos(obj.pos),
		action: obj.action,
		shape: obj.shape,
		lastLeftRight: obj.lastLeftRight,
		alpha: obj.sprite ? obj.sprite.alpha : 1
	};
}

function applyRunnerObj(obj)
{
	if(!runner || !obj) return;

	runner.pos = unpackPos(obj.pos);
	runner.action = safeNumber(obj.action, ACT_STOP);
	runner.shape = obj.shape || "runRight";
	runner.lastLeftRight = obj.lastLeftRight || ACT_RIGHT;

	setSpritePosition(runner.sprite, runner.pos);
	runner.sprite.alpha = typeof obj.alpha == "number" ? obj.alpha : 1;
	gotoSpriteShape(runner.sprite, runner.shape, runner.action == ACT_STOP);
}

function packGuardObj()
{
	var data = [];
	for(var i = 0; i < guardCount; i++) {
		data.push({
			pos: packPos(guard[i].pos),
			action: guard[i].action,
			shape: guard[i].shape,
			lastLeftRight: guard[i].lastLeftRight,
			hasGold: guard[i].hasGold || 0,
			holePos: guard[i].holePos ? packPos(guard[i].holePos) : null,
			curFrameIdx: guard[i].curFrameIdx,
			curFrameTime: guard[i].curFrameTime,
			shapeFrame: guard[i].shapeFrame ? guard[i].shapeFrame.slice(0) : null
		});
	}
	return data;
}

function applyGuardObj(data)
{
	var max = Math.min(guardCount, data.length);
	for(var i = 0; i < max; i++) {
		var src = data[i];
		guard[i].pos = unpackPos(src.pos);
		guard[i].action = safeNumber(src.action, ACT_STOP);
		guard[i].shape = src.shape || "runLeft";
		guard[i].lastLeftRight = src.lastLeftRight || ACT_LEFT;
		guard[i].hasGold = src.hasGold || 0;
		guard[i].holePos = src.holePos ? unpackPos(src.holePos) : null;
		guard[i].curFrameIdx = safeNumber(src.curFrameIdx, 0);
		guard[i].curFrameTime = safeNumber(src.curFrameTime, -1);
		guard[i].shapeFrame = src.shapeFrame ? src.shapeFrame.slice(0) : null;

		if(redhatMode && guard[i].hasGold > 0) guardWearRedhat(guard[i]);
		else guardRemoveRedhat(guard[i]);

		setSpritePosition(guard[i].sprite, guard[i].pos);
		gotoSpriteShape(guard[i].sprite, guard[i].shape, guard[i].action == ACT_STOP);
	}
}

function applyAnimationQueues(queues)
{
	shakingGuardList = packNumberArray(queues.shakingGuardList || []);
	rebornGuardList = packNumberArray(queues.rebornGuardList || []);

	for(var i = 0; i < shakingGuardList.length; i++) {
		var shakeId = shakingGuardList[i];
		if(shakeId >= 0 && shakeId < guardCount) {
			if(!guard[shakeId].shapeFrame) {
				guard[shakeId].shapeFrame = guard[shakeId].shape == "shakeLeft" ? shakeLeft.slice(0) : shakeRight.slice(0);
			}
			var frameIdx = clampNumber(guard[shakeId].curFrameIdx, 0, guard[shakeId].shapeFrame.length-1, 0);
			gotoSpriteShape(guard[shakeId].sprite, guard[shakeId].shapeFrame[frameIdx], true);
		}
	}

	for(i = 0; i < rebornGuardList.length; i++) {
		var rebornId = rebornGuardList[i];
		if(rebornId >= 0 && rebornId < guardCount) {
			var rebornIdx = clampNumber(guard[rebornId].curFrameIdx, 0, rebornFrame.length-1, 0);
			gotoSpriteShape(guard[rebornId].sprite, rebornFrame[rebornIdx], true);
		}
	}
}

function packMapObj()
{
	var data = [];
	for(var y = 0; y < NO_OF_TILES_Y; y++) {
		var row = [];
		for(var x = 0; x < NO_OF_TILES_X; x++) {
			var cell = {
				b: map[x][y].base,
				a: map[x][y].act
			};
			if(map[x][y].bitmap) cell.o = map[x][y].bitmap.alpha;
			row.push(cell);
		}
		data.push(row);
	}
	return data;
}

function applyMapObj(data)
{
	for(var x = 0; x < NO_OF_TILES_X; x++) {
		for(var y = 0; y < NO_OF_TILES_Y; y++) {
			if(map[x][y].bitmap) mainStage.removeChild(map[x][y].bitmap);
			map[x][y].bitmap = null;
		}
	}

	for(y = 0; y < NO_OF_TILES_Y; y++) {
		for(x = 0; x < NO_OF_TILES_X; x++) {
			var src = data[y] && data[y][x] ? data[y][x] : { b: EMPTY_T, a: EMPTY_T };
			map[x][y].base = safeNumber(src.b, EMPTY_T);
			map[x][y].act = safeNumber(src.a, EMPTY_T);

			var bitmapName = bitmapNameForTile(map[x][y].base);
			if(bitmapName) {
				var tile = map[x][y].bitmap = getThemeBitmap(bitmapName);
				tile.setTransform(x * tileWScale, y * tileHScale, tileScale, tileScale);
				if(typeof src.o == "number") tile.set({alpha: src.o});
				mainStage.addChild(tile);
			}
		}
	}
}

function bitmapNameForTile(tile)
{
	switch(tile) {
	case BLOCK_T:
	case TRAP_T:
		return "brick";
	case SOLID_T:
		return "solid";
	case LADDR_T:
	case HLADR_T:
		return "ladder";
	case BAR_T:
		return "rope";
	case GOLD_T:
		return "gold";
	default:
		return null;
	}
}

function packHoleObj()
{
	if(!holeObj || holeObj.action != ACT_DIGGING) return { action: ACT_STOP };
	return {
		action: holeObj.action,
		pos: packPos(holeObj.pos),
		shapeFrame: holeObj.shapeFrame ? holeObj.shapeFrame.slice(0) : null,
		curFrameIdx: holeObj.curFrameIdx || 0,
		currentAnimationFrame: holeObj.sprite ? holeObj.sprite.currentAnimationFrame : 0
	};
}

function applyHoleObj(data)
{
	if(!holeObj || !holeObj.sprite) return;
	holeObj.sprite.removeAllEventListeners("animationend");
	holeObj.action = ACT_STOP;

	if(!data || data.action != ACT_DIGGING) return;

	holeObj.action = ACT_DIGGING;
	holeObj.pos = unpackPos(data.pos);
	holeObj.shapeFrame = data.shapeFrame ? data.shapeFrame.slice(0) : digHoleLeft.slice(0);
	holeObj.curFrameIdx = clampNumber(data.curFrameIdx, 0, holeObj.shapeFrame.length-1, 0);
	holeObj.sprite.setTransform(holeObj.pos.x * tileWScale, holeObj.pos.y * tileHScale, tileScale, tileScale);
	gotoSpriteShape(holeObj.sprite, holeObj.shapeFrame[holeObj.curFrameIdx], true);
	holeObj.sprite.currentAnimationFrame = safeNumber(data.currentAnimationFrame, holeObj.curFrameIdx);
	mainStage.addChild(holeObj.sprite);
}

function packFillHoleObj()
{
	var data = [];
	for(var i = 0; i < fillHoleObj.length; i++) {
		data.push({
			pos: packPos(fillHoleObj[i].pos),
			curFrameIdx: fillHoleObj[i].curFrameIdx || 0,
			curFrameTime: fillHoleObj[i].curFrameTime || 0
		});
	}
	return data;
}

function applyFillHoleObj(data)
{
	fillHoleObj = [];
	for(var i = 0; i < data.length; i++) {
		var fillPos = unpackPos(data[i].pos);

		// Reuse the existing game factory so the restored object has the same
		// CreateJS setup and event wiring as a normally-created fill hole.
		fillHole(fillPos.x, fillPos.y);

		var fillSprite = fillHoleObj[fillHoleObj.length - 1];
		if(!fillSprite) continue;

		fillSprite.pos = fillPos;
		fillSprite.curFrameIdx = clampNumber(data[i].curFrameIdx, 0, fillHoleFrame.length-1, 0);
		fillSprite.curFrameTime = safeNumber(data[i].curFrameTime, -1);
		fillSprite.setTransform(fillSprite.pos.x * tileWScale, fillSprite.pos.y * tileHScale, tileScale, tileScale);

		if(curAiVersion >= 3) {
			gotoSpriteShape(fillSprite, fillHoleFrame[fillSprite.curFrameIdx], true);
		}
	}
}

function packPos(pos)
{
	return {
		x: pos ? safeNumber(pos.x, 0) : 0,
		y: pos ? safeNumber(pos.y, 0) : 0,
		xOffset: pos ? safeNumber(pos.xOffset, 0) : 0,
		yOffset: pos ? safeNumber(pos.yOffset, 0) : 0
	};
}

function unpackPos(pos)
{
	return {
		x: clampNumber(pos ? pos.x : 0, 0, maxTileX, 0),
		y: clampNumber(pos ? pos.y : 0, 0, maxTileY, 0),
		xOffset: pos ? safeNumber(pos.xOffset, 0) : 0,
		yOffset: pos ? safeNumber(pos.yOffset, 0) : 0
	};
}

function setSpritePosition(sprite, pos)
{
	if(!sprite || !pos) return;
	sprite.x = (pos.x * tileW + pos.xOffset) * tileScale | 0;
	sprite.y = (pos.y * tileH + pos.yOffset) * tileScale | 0;
}

function gotoSpriteShape(sprite, shape, stopped)
{
	if(!sprite || !shape) return;
	try {
		if(stopped) sprite.gotoAndStop(shape);
		else sprite.gotoAndPlay(shape);
	} catch(e) {
		try {
			sprite.gotoAndStop(0);
		} catch(ignore) {}
	}
}

function packNumberArray(data)
{
	var list = [];
	if(!data) return list;
	for(var i = 0; i < data.length; i++) {
		var n = parseInt(data[i], 10);
		if(!isNaN(n)) list.push(n);
	}
	return list;
}

function safeNumber(value, fallback)
{
	var n = Number(value);
	return isNaN(n) ? fallback : n;
}

function clampNumber(value, min, max, fallback)
{
	var n = safeNumber(value, fallback);
	if(n < min) return min;
	if(n > max) return max;
	return n;
}
