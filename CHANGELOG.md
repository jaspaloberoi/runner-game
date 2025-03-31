# Runner Game - Changelog

## Version 3.1 (Current)
**Feature Update: Themed Levels & Enhanced Mode Transitions**

### Themed Levels
- Implemented 4 distinct themed backgrounds that cycle as player progresses:
  - Level 1: Clouds in Clear Sky - Fluffy white clouds moving across a bright blue sky
  - Level 2: Sunset with Nice Sun - Warm pastel colors with sun rays and horizon clouds
  - Level 3: Rain with Dark Clouds - Multiple rain clouds and animated rainfall
  - Level 4: Night Sky with Twinkling Stars - Distributed stars throughout the entire sky with a detailed moon

### Mode Transition Improvements
- Fixed Orange mode activation logic with reliable timing-based mode cycling
- Green mode can now only be activated from Normal mode
- Added detailed color cycle system (Yellow → Orange → Green) with 300ms intervals
- Improved mode diagnostic logging for better debugging
- Fixed mode transition animations to prevent game state conflicts

### Visual Enhancements
- Increased moon size by 2× in night level with detailed crater shadows
- Added bird outline for better visibility across all level backgrounds
- Completely restored the orange trail effect with proper Bezier curve drawing
- Enhanced green bubble visualization with shimmer effects and timer indicator
- Added smooth sin-based animation for bird shake effect

### Gameplay and UX Refinements
- Added "tap to start" screen instead of auto-starting the game
- Fixed level cycling (changes every 2 obstacles, cycles from 1-4)
- Improved game state handling to ensure proper initialization
- Fixed numerous edge cases with mode transitions that could cause incorrect behavior

## Version 3.0
**Stable Release: Mode Refinements & Performance Improvements**

### Game Mode Enhancements
- Renamed Pink Mode to Green Mode with completely new visual appearance
- Increased Green Mode speed to 3.0× for more dramatic gameplay differences
- Ensured Green Mode bubble is semi-transparent for better visibility
- Optimized Green Mode drag handling for smoother control
- Fixed mode transition animations and effects
- Fixed Yellow mode jump functionality in all gameplay scenarios

### Visual and Animation Improvements
- Implemented bird shake effect when transitioning back to Yellow mode
- Shake occurs before gravity or speed changes for smoother visual effect
- Completely removed bird scaling in all modes for consistent gameplay feel
- Ensured bird maintains fixed size throughout all levels and mode transitions

### Sound System Polishing
- Fixed score sound looping issue
- Limited score sound to 2 seconds duration
- Ensured score sound only plays during level changes
- Fixed sound behavior during mode transitions

## Version 2.5
**Feature Update: Enhanced Gameplay Balance & Mechanics**

### Game Mode Improvements
- Fixed Pink Mode activation issues to ensure consistent color changes
- Increased speed in Orange and Pink modes to 2.0× (previously 1.5× and 1.25×)
- Made Yellow mode jumps 1.5× higher for better maneuverability
- Removed bird scaling across all modes for consistent gameplay
- Bird now maintains the same size throughout all levels and modes

### Obstacle System Refinements
- Significantly reduced the frequency of moving obstacles in early levels
- Level 1: Moving obstacles extremely rare (every 12-18 obstacles)
- Level 2: Moving obstacles very rare (every 8-14 obstacles) 
- Levels 3-4: Fewer moving obstacles (every 6-10 obstacles)
- Fixed redundant obstacle movement determination system

### UI and Movement Improvements
- Moved score and high score display to the right side of the screen
- Removed top boundary restriction - bird can now fly all the way to screen top
- Fixed Pink Mode vertical movement to allow full screen navigation
- Improved log messages for better debugging

## Version 2.1
**Feature Update: Orange State Power-Up**

### Orange State Power-Up
- Added orange state power-up activated by holding for 0.2 seconds
- Enhanced jump mechanics in orange state with 3.25× multiplier
- Increased bird size by 20% during orange state
- Bird color transitions from yellow to orange based on tap duration
- Added temporary speed boost during orange state
- Orange state lasts for 5 seconds before returning to normal
- Double-beep sound effect for orange state jumps

### Jump Mechanics
- Improved jump responsiveness and physics
- Refined tap duration-based jump height mechanics
- Removed position adjustment on jump for more natural physics
- Added smooth transition between different jump heights
- Fine-tuned jump multipliers for better gameplay feel

### Visual Improvements
- Added color transition from yellow to orange during tap
- Bird grows larger during orange state
- Enhanced orange color visibility for clearer game state indication

## Version 2.0
**Major Update: Improved Gameplay Balance and Sound Management**

### Obstacle System Improvements
- Implemented dynamic obstacle spacing with level-based difficulty adjustment
- Fixed overlapping obstacle issue with smarter positioning logic
- Added randomized gaps between obstacles for more varied gameplay
- Reduced movement range for moving obstacles, especially in early levels
- Decreased movement speed of obstacles in early levels for better progression
- Made moving obstacles appear less frequently in early game (level-based frequency)
- Improved obstacle type distribution (more narrow obstacles in early levels)
- Added progressive introduction of spiked obstacles starting from level 3

### Sound System Improvements
- Fixed level-up sound continuing after game over
- Added comprehensive error handling to prevent sound-related crashes
- Implemented a centralized sound management system
- Reduced level-up sound duration from 4 to 2 seconds
- Ensured proper sound cleanup on game reset

### Gameplay Balance
- Easier early levels with appropriate difficulty progression
- First obstacle now starts further away, giving players more reaction time
- Increased the base spacing between obstacles by 3x (from 5% to 15% of screen width)
- Added early level bonuses for obstacle spacing (+20% in levels 1-2, +10% in levels 3-4)

### Visual and Control Improvements
- Enhanced bird control with tap duration affecting jump height (1x to 3x multiplier)
- Implemented color-changing bird mechanic based on tap duration
- Framework for future color-based abilities

## Version 1.3
**Feature Update: Enhanced Controls and Movement**

- Smooth scrolling with key-based Canvas recomposition
- Precise collision detection with full hitboxes
- Screen shake effect on collision
- Optimized rendering for better performance
- Moving obstacles appearing every 3-6 obstacles
- Tap duration-based jump height system (1x to 2x based on 0.5s tap)
- Faster falling speed (1.5x) for more challenging gameplay
- Visual tap duration indicator
- Improved obstacle spacing

## Version 1.2
**Feature Update: Level Progression and Obstacle Variety**

- Added level-up system (every 10 obstacles passed)
- Increased bird size on level-up
- Introduced different obstacle types (narrow, normal, wide, spiked)
- Added basic sound effects (jump, collision, level-up)
- Improved collision detection
- Increased game speed based on level progression

## Version 1.1
**Feature Update: Basic Gameplay Improvements**

- Added high score tracking with persistent storage
- Implemented obstacle scoring system
- Improved bird physics with better jumping mechanics
- Added ground collision detection
- Basic obstacle generation
- Simple game reset on collision

## Version 1.0
**Initial Release: Core Functionality**

- Basic runner game mechanics
- Scrolling background
- Simple square bird with jump ability
- Basic obstacle generation
- Collision detection
- Simple score tracking 