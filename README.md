Lode Runner - Total Recall
=======================================
## (超級運動員 - 全面回憶)

This program build with Javascript + [CreateJS](http://www.createjs.com).

### * 3 GAME Mode & 1 DEMO Mode
<table>
<tr>
<td><b>1. Challenge Mode</b></td> 
<td>Compete with other players.</td>
</tr>
<tr>
<td><b>2. Training Mode</b></td> 
<td>Player can select any levels</td>
</tr>
<tr>
<td><b>3. Edit Mode</b></td> 
<td>Player can create custom levels</td>
</tr>
<tr>
<td><b>4. Demo Mode</b></td> 
<td>Demo passed levels</td>
</tr>

</table>

### * Include 5 Game Versions
<table>
<tr>
<td><b>1. <a target="_blank" href="https://en.wikipedia.org/wiki/Lode_Runner">Classic Lode Runner</a></b></td>
<td>(150 Levels)</td>
<td>Difficulty: 3</td>
</tr>

<tr>
<td><b>2. <a target="_blank" href="http://www.gb64.com/game.php?id=5906&d=42">Professional Lode Runner</a></b></td> 
<td>(150 Levels)</td>
<td>Difficulty: 4</td>
</tr>

<tr>
<td><b>3. <a target="_blank" href="http://www.vizzed.com/play/revenge-of-lode-runner-appleii-online-apple-ii-6223-game">Revenge of Lode Runner</a></b></td> 
<td>(17 Levels)</td>
<td>Difficulty: 4</td>
</tr>

<tr>
<td><b>4. <a target="_blank" href="http://www.spoonbillsoftware.com.au/loderunner.htm">Lode Runner Fan Book</a></b></td> 
<td>(66 Levels)</td>
<td>Difficulty: 5</td>
</tr>

<tr>
<td><b>5. <a target="_blank" href="https://en.wikipedia.org/wiki/Championship_Lode_Runner">Championship Lode Runner</a></b></td> 
<td>(51 Levels)</td>
<td>Difficulty: 5</td>
</tr>
</table>

### * Provide 2 Themes
<table>
<tr>
<td valign="middle">1.</td>
<td valign="middle"><img src="image/apple2.png" height="23" width="20"></td>
<td><b>APPLE-II</b></td> 
</tr>
<tr>
<td valign="middle">2.</td>
<td valign="middle"><img src="image/commodore64.png" height="23" width="20"></td>
<td valign="middle"><b>Commodore 64</b></td> 
</tr>
</table>

### * 2 Keyboard control mode

<table>
<tr>
<td valign="middle">1.</td>
<td valign="middle"><img src="image/repeatOn.png" height="24" width="24"></td>
<td valign="middle"><b>Repeat Actions On:</b> Like APPLE-II keyboard behavior</td> 
</tr>
<tr>
<td valign="middle">2.</td>
<td valign="middle"><img src="image/repeatOff.png" height="24" width="24"></td>
<td valign="middle"><b>Repeat Actions Off:</b> Like NES keyboard behavior</td> 
</tr>
</table>

### * Save and Load Current Game State

The game menu now includes two additional actions:

<table>
<tr>
<td><b>1. Save Game State</b></td>
<td>Saves the current in-progress Challenge or Training game into the browser's local storage.</td>
</tr>
<tr>
<td><b>2. Load Game State</b></td>
<td>Restores the previously saved in-progress game state from the same browser and same local address.</td>
</tr>
</table>

The saved state is stored in a dedicated browser `localStorage` slot and does not replace the existing progress, high-score, custom-level backup, or restore data.

The save slot includes the current level state, player position, guards, score, lives, holes, collected gold, map changes, timers, and related animation queues. It is intended as a single local resume slot for the current browser. It is not cloud-synced and is not shared across different browsers, devices, hostnames, or HTTP ports.

### * Play on a Local Machine
#### A simple web server is required to play LoderRunner on a local machine; follow these instructions for Windows:
1. Download and extract the source code into a directory.
2. If Python is installed, open a command prompt, navigate to the LodeRunner directory, and run "`python -m http.server 8080`" to start an HTTP server.
3. Alternatively, download `sswws.zip` from [here](https://github.com/faustinoaq/sswws/releases/tag/v0.1.0), extract it into the LodeRunner directory, and run "`sswws.exe -p 8080`" from the command prompt.
4. Open a web browser and go to "`http://127.0.0.1:8080/lodeRunner.html`".
#####  <font color="red">Note:</font><font color="blue"> Changing the hostname or HTTP port will cause the browser to store "status", "custom levels", and "saved game state" data in a separate storage.</font>

### * <a target="_blank" href="https://simonhung.github.io/LodeRunner_TotalRecall/lodeRunner.html">Play Online</a>
------------------------------------
