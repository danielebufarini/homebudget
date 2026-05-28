/**
 * Save/load support for the current in-progress game.
 *
 * This feature intentionally uses a dedicated localStorage slot so it does not
 * interfere with the game's existing progress, high-score, custom-level backup,
 * or restore mechanisms.
 */

var GAME_STATE_SAVE_VERSION = 1;

function hasSavedGameState()
{
	return getStorage(STORAGE_GAME_STATE) != null;
}

function canSaveGameState()
{
	if(playMode !== PLAY_CLASSIC && playMode != PLAY_MODERN) return false;
	if(changingLevel || !map || !runner) return false;
	if(gameState === GAME_START || gameState == GAME_RUNNING) return true;
	if(gameState == GAME_PAUSE && (lastGameState == GAME_START || lastGameState == GAME_RUNNING)) return true;
	return false;
}

function saveGameStateMenu(id, callbackFun)
{
	var saved = saveCurrentGameState(0);
	if(callbackFun) callbackFun();
	setTimeout(function() {
		showTipsText(saved ? "GAME STATE SAVED" : "CANNOT SAVE NOW", 2500);
	}, 50);
}

function loadGameStateMenu(id, callbackFun)
{
	if(callbackFun) callbackFun();
	loadCurrentGameState(1);
}

function saveCurrentGameState(showMsg)
{
	if(!canSaveGameState()) {
		if(showMsg) showTipsText("CANNOT SAVE NOW", 2500);
		return false;
	}

	try {
		var snapshot = buildGameStateSnapshot();
		setStorage(STORAGE_GAME_STATE, JSON.stringify(snapshot));
		if(showMsg) showTipsText("GAME STATE SAVED", 2500);
		return true;
	} catch(e) {
		error("saveCurrentGameState failed: " + e.message);
		if(showMsg) showTipsText("SAVE FAILED", 2500);
		return false;
	}
}

function loadCurrentGameState(showMsg)
{
	var infoJSON = getStorage(STORAGE_GAME_STATE);
	if(infoJSON == null) {
		if(showMsg) showTipsText("NO SAVED GAME", 2500);
		return false;
	}

	try {
		var snapshot = JSON.parse(infoJSON);
		if(!validateGameStateSnapshot(snapshot)) {
			if(showMsg) showTipsText("INVALID SAVE DATA", 2500);
			return false;
		}

		restoreGameStateSnapshot(snapshot);
		if(showMsg) showTipsText("GAME STATE LOADED", 2500);
		return true;
	} catch(e) {
		error("loadCurrentGameState failed: " + e.message);
		if(showMsg) showTipsText("LOAD FAILED", 2500);
		return false;
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
			guard[shakeId].sprite.gotoAndStop(guard[shakeId].shapeFrame[frameIdx]);
		}
	}

	for(i = 0; i < rebornGuardList.length; i++) {
		var rebornId = rebornGuardList[i];
		if(rebornId >= 0 && rebornId < guardCount) {
			var rebornIdx = clampNumber(guard[rebornId].curFrameIdx, 0, rebornFrame.length-1, 0);
			guard[rebornId].sprite.gotoAndStop(rebornFrame[rebornIdx]);
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
	holeObj.sprite.gotoAndStop(holeObj.shapeFrame[holeObj.curFrameIdx]);
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
		sprite.gotoAndStop(0);
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
