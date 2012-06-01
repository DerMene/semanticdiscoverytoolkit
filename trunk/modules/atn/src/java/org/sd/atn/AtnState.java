/*
    Copyright 2009 Semantic Discovery, Inc. (www.semanticdiscovery.com)

    This file is part of the Semantic Discovery Toolkit.

    The Semantic Discovery Toolkit is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    The Semantic Discovery Toolkit is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with The Semantic Discovery Toolkit.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.sd.atn;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.sd.token.CategorizedToken;
import org.sd.token.Token;
import org.sd.util.Usage;
import org.sd.util.tree.Tree;

/**
 * Container class for a processing state to pair a grammar rule step with
 * an input token and generate successive states.
 * <p>
 * This is the main machine controller class for traversal of states, during
 * which a State tree is constructed, preserving the paths of states visited
 * and from which parse trees are constructed.
 *
 * @author Spence Koehler
 */
@Usage(notes =
       "Container class for a processing state to pair a grammar rule step with\n" +
       "an input token and generate successive states.\n" +
       "\n" +
       "This is the main machine controller class for traversal of states, during\n" +
       "which a State tree is constructed, preserving the paths of states visited\n" +
       "and from which parse trees are constructed."
  )
public class AtnState {
  
  private Token inputToken;
  public Token getInputToken() {
    return inputToken;
  }

  private AtnRule rule;
  public AtnRule getRule() {
    return rule;
  }

  private int stepNum;
  int getStepNum() {
    return stepNum;
  }

  private int repeatNum;
  public int getRepeatNum() {
    return repeatNum;
  }


  private Tree<AtnState> parentStateNode;
  public Tree<AtnState> getParentStateNode() {
    return parentStateNode;
  }

  AtnParseOptions parseOptions;
  int skipNum;


  private MatchResult matchResult;
  public boolean getMatched() {
    return matchResult == null ? false : matchResult.matched();
  }
  MatchResult getMatchResult() {
    return matchResult;
  }
  void setMatchResult(MatchResult matchResult) {
    this._nextToken = null;
    this.matchResult = matchResult;
  }

  private AtnState pushState;
  public AtnState getPushState() {
    return pushState;
  }

  private boolean isPoppedState;
  boolean isPoppedState() {
    return isPoppedState;
  }

  private int popCount;
  int getPopCount() {
    return popCount;
  }

  public boolean isRepeat() {
    return repeatNum > 0;
  }

  private boolean _isSkipped;
  public boolean isSkipped() {
    return _isSkipped;
  }

  public boolean isRuleEnd() {
    return isRuleEnd(true);
  }

  public boolean isRuleEnd(boolean verifyPop) {
    boolean result = (rule != null) ? rule.isTerminal(stepNum) : false;

    if (result && verifyPop) {
      result = verifyPop();
    }

    return result;
  }

  public boolean isRuleStart() {
    return pushState == getParentState();
  }

  private boolean verifyPop() {
    return rule.verifyPop(this.inputToken, this);
  }

  private AtnRuleStep _ruleStep;
  public AtnRuleStep getRuleStep() {
    if (_ruleStep == null) {
      _ruleStep = rule.getSteps().get(stepNum);
    }
    return _ruleStep;
  }

  private boolean computedNextToken;
  private Token _nextToken;
  private boolean popFailed;


  /**
   * Information used for verifying and incrementing a considered state
   * (token with rule step) match.
   */
  AtnState(Token inputToken, AtnRule rule, int stepNum, Tree<AtnState> parentStateNode,
           AtnParseOptions parseOptions, int repeatNum, int numSkipped, AtnState pushState) {
    this.inputToken = inputToken;
    this.rule = rule;
    this.stepNum = stepNum;
    this.parentStateNode = parentStateNode;
    this.parseOptions = parseOptions;
    this.repeatNum = repeatNum;
    this.skipNum = numSkipped;
    this.matchResult = null;
    this.pushState = pushState;
    this.popCount = 0;
    this._isSkipped = false;
    this.computedNextToken = false;
    this._nextToken = null;
    this.popFailed = false;
  }

  /** Copy constructor */
  AtnState(AtnState other) {
    this.inputToken = other.inputToken;
    this.rule = other.rule;
    this.stepNum = other.stepNum;
    this.parentStateNode = other.parentStateNode;
    this.parseOptions = other.parseOptions;
    this.repeatNum = other.repeatNum;
    this.skipNum = other.skipNum;
    this.matchResult = other.matchResult;
    this.pushState = other.pushState;
    this.isPoppedState = other.isPoppedState;
    this.popCount = other.popCount;
    this._isSkipped = other._isSkipped;
    this._ruleStep = other._ruleStep;
    this.computedNextToken = other.computedNextToken;
    this._nextToken = other._nextToken;
    this.popFailed = other.popFailed;
  }

  /**
   * Determine whether this instance (if verified to match) is a valid end.
   */
  boolean isValidEnd(Set<Integer> stopList) {
    boolean result = false;

    if (getMatched() && isRuleEnd() && isPushEnd() && !popFailed) {
      final Token nextToken = getNextToken(stopList);
      result = (nextToken == null) || !parseOptions.getConsumeAllText();
    }

    return result;
  }

  boolean popFailed() {
    return popFailed;
  }

  /**
   * Get this state's node in the state tree (if it exists).
   */
  public Tree<AtnState> getStateNode() {
    Tree<AtnState> result = null;

    if (parentStateNode != null && parentStateNode.hasChildren()) {
      for (Tree<AtnState> child : parentStateNode.getChildren()) {
        if (this == child.getData()) {
          result = child;
          break;
        }
      }
    }

    return result;
  }

  /**
   * Determine whether all push states would pop to rule ends.
   */
  private boolean isPushEnd() {
    boolean result = true;

    for (AtnState curPushState = pushState; curPushState != null; curPushState = curPushState.pushState) {
      // check for rule end, but don't verifyPop because push is not necessarily at end of constituent
      if (!curPushState.isRuleEnd(false)) {
        result = false;
        break;
      }
    }

    return result;
  }

  /**
   * Get a "pop" state based on this instance's push state and this state as
   * the end state, where a "pop" state is a temporary state used to generate
   * the next states in a parent rule after completing a 'pushed' rule.
   * 
   * The pop state also acts as a marker when constructing a parse tree
   * from the states tree to identify when to move back up to a parent
   * in the parse tree for adding subsequent children from later matched
   * states.
   */
  AtnState popState(Tree<AtnState> parentStateNode) {
    AtnState result = null;

    if (!isPoppedState) {
      result = new AtnState(inputToken, this.rule, this.stepNum,
                            parentStateNode, this.parseOptions,
                            this.repeatNum, this.skipNum,
                            this.pushState);
      result.isPoppedState = true;
      result.popCount = 1;
    }
    else if (pushState != null) {
      // verify rule (constituent) with the (pushState.)rule test
      result = new AtnState(inputToken, pushState.rule, pushState.stepNum,
                            parentStateNode, pushState.parseOptions,
                            pushState.repeatNum, pushState.skipNum,
                            pushState.pushState);
      result.isPoppedState = true;
      result.popCount = this.popCount + 1;
    }

    return result;
  }

  boolean reachedTokenLimit() {
    boolean result = false;

    if (!result) {
      final AtnRuleStep ruleStep = getRuleStep();
      final int stepRepeatLimit = ruleStep.getRepeatLimit();
      if (stepRepeatLimit > 0) {
        result = getRepeatNum() >= stepRepeatLimit;
      }
    }

    if (!result) {
      final AtnRule rule = getRule();
      final int ruleTokenLimit = rule.getTokenLimit();
      if (ruleTokenLimit > 0) {
        final int constituentTokenCount = AtnStateUtil.countConstituentTokens(this);
        result = constituentTokenCount >= ruleTokenLimit;
      }
    }

    return result;
  }

  /**
   * Get the next state for a repeat of the step if the step could
   * repeat.
   */
  AtnState getNextRepeatState(Tree<AtnState> curStateNode, AtnState referenceState, boolean incToken, Set<Integer> stopList) {
    AtnState result = null;

    if (referenceState == null) referenceState = this;

    if (referenceState.getRuleStep().repeats() && !referenceState.reachedTokenLimit()) {
      if (incToken && !getRuleStep().consumeToken()) incToken = false;

      final Token nextToken = incToken ? getNextToken(stopList) : this.inputToken;
      if (nextToken != null) {
        result =
          new AtnState(
            nextToken, rule, stepNum,
            curStateNode, parseOptions, repeatNum + (incToken ? 1 : 0), 0, pushState);
      }
    }

    return result;
  }

  /**
   * Get the next state for incrementing to the next rule step if
   * incrementing is possible.
   */
  AtnState getNextStepState(Tree<AtnState> curStateNode, boolean incToken, Set<Integer> stopList) {
    AtnState result = null;

    if (!rule.isLast(stepNum)) {
      final int nextStepNum = getNextStepNum();
      if (nextStepNum >= 0) {

        if (incToken && !getRuleStep().consumeToken()) incToken = false;

        final Token nextToken = incToken ? getNextToken(stopList) : this.inputToken;
        if (nextToken != null) {
          result =
            new AtnState(
              nextToken, rule, nextStepNum,
              curStateNode, parseOptions, 0, 0, pushState);
        }
      }
    }

    return result;
  }

  /**
   * Get the next state for retrying this info's step with a revised
   * input token.
   */
  AtnState getNextRevisedState() {
    AtnState result = null;

    if (getRuleStep().consumeToken()) {
      final Token nextToken = computeRevisedToken();

      if (nextToken != null) {
        result = new AtnState(
          nextToken, rule, stepNum,
          parentStateNode, parseOptions, repeatNum, skipNum, pushState);
      }
    }

    return result;
  }

  AtnState getSkipOptionalState() {
    AtnState result = null;

    if (getRuleStep().isOptional()) {
      if (!rule.isLast(stepNum)) {
        final int nextStepNum = getNextStepNum();
        if (nextStepNum >= 0) {
          // increment step
          result =
            new AtnState(
              inputToken, rule, nextStepNum,
              parentStateNode, parseOptions, 0, 0, pushState);
        }
      }
      // else, return null and let caller add Pop state
    }

    return result;
  }

  private boolean canBeSkipped() {
    boolean result = (skipNum + inputToken.getWordCount() - 1) < Math.max(parseOptions.getSkipTokenLimit(), getRuleStep().getSkip());

    if (result && !parseOptions.getConsumeAllText()) {
      // when not consuming all text,
      // disable skip functionality for first matching rule step in a rule
      final AtnState parentState = (parentStateNode != null) ? parentStateNode.getData() : null;
      if (parentState == null || !(parentState.getMatched() || parentState.isPoppedState() || parentState.isSkipped())) {
        result = false;
      }
    }

    return result;
  }

  AtnState getNextSkippedState(Tree<AtnState> curStateNode, Set<Integer> stopList) {
    AtnState result = null;

    if (canBeSkipped()) {
      // increment the token, not the rule step
      final Token nextToken = getNextSmallestToken(stopList);
      if (nextToken != null) {
        markAsSkipped();
        result = new AtnState(nextToken, rule, stepNum, curStateNode, parseOptions, repeatNum, skipNum, pushState);
      }
    }

    return result;
  }


  /**
   * Get the (possibly cached) next token following this instance's considered state.
   */
  Token getNextToken(Set<Integer> stopList) {
    if (!computedNextToken) {
      _nextToken = computeNextToken(inputToken);

      if (_nextToken != null && stopList != null && stopList.contains(_nextToken.getStartIndex())) {
        _nextToken = null;
      }

      computedNextToken = true;
    }
    return _nextToken;
  }

  private Token getNextSmallestToken(Set<Integer> stopList) {
    Token result = inputToken.getNextSmallestToken();

    if (result != null) {
      result = rule.getGrammar().getAcceptedToken(rule.getTokenFilterId(), result, false, inputToken, false, false, this);

      if (result != null && stopList != null && stopList.contains(result.getStartIndex())) {
        result = null;
      }
    }

    return result;
  }

  /**
   * Get the next step num or -1 if there isn't another step after all.
   */
  private final int getNextStepNum() {
    int result = stepNum + 1;

    // check the step's 'require' attribute
    while (true) {
      final AtnRuleStep step = rule.getStep(result);
      if (step == null) {
        result = -1;
        break;
      }

      // Check for 'unless' to see if we can skip considering this next step
      // Note that we won't fully know if we have met 'require' constraints
      // until we get to that state, so we won't rule out 'requires' just yet.
      // Also, the 'unless' constraint will need to be checked again once
      // we've parsed through the current constituent.

      final String[] unless = step.getUnless();
      if (unless == null || !haveRequired(unless, true)) {
        break;
      }
      // if requirements aren't met, increment and loop
      else {
        ++result;
      }
    }

    return result;
  }

  // Double-Check require and unless with new information of considering this state in context
  private final boolean meetsRequirements() {

    final AtnRuleStep step = rule.getStep(stepNum);
    final String[] require = step.getRequire();
    boolean result = (require == null || haveRequired(require, false));

    if (result) {
      final String[] unless = step.getUnless();
      if (unless != null) {
        result = !haveRequired(unless, false);
      }
    }
    

    return result;
  }

  private final boolean haveRequired(String[] requires, boolean includeThisState) {
    boolean result = false;

    for (String require : requires) {
      if (includeThisState) {
        result = AtnStateUtil.matchesCategory(this, require);
      }

      if (!result) {
        // haven't verified yet, look back in state history
        final int[] levelDiff = new int[]{0};
        final AtnState priorMatch = AtnStateUtil.findPriorMatch(this, require, levelDiff);

        if (priorMatch != null) {
          // can find match 'down' (pushed), but not up (popped)
          result = (levelDiff[0] <= 0);
        }
      }

      if (result) break;
    }

    return result;
  }

  private String showStateTree() {
    return showStateTree(true);
  }

  /**
   * Show the full state tree and/or the state path.
   */
  private String showStateTree(boolean fullTree) {
    final StringBuilder result = new StringBuilder();

    result.
      append("\tPath:").
      append(showStatePath());

    if (fullTree) {
      final Tree<AtnState> myTree = getStateNode();

      if (myTree == null) {
        result.
          append("\n\tFullTree (to parent):").
          append(AtnStateUtil.showStateTree(parentStateNode));
      }
      else {
        result.
          append("\n\tFullTree (to self):").
          append(AtnStateUtil.showStateTree(myTree));
      }
    }

    return result.toString();
  }

  /**
   * Show the states from this up to the root.
   */
  private String showStatePath() {
    return AtnStateUtil.showStatePath(this);
  }

  /**
   * Show this state with its push path and constituent start.
   */
  private String showStateContext() {
    final StringBuilder result = new StringBuilder();

    final List<AtnState> pushPath = getPushPath();
    if (pushPath != null) {
      for (AtnState pushState : pushPath) {
        if (result.length() == 0) {
          result.
            append(pushState.getRule().getRuleName()).
            append(" : ").
            append(pushState.getRuleStep().getCategory());
        }
        else {
          result.append(" : ").append(pushState.getRuleStep().getCategory());
        }
      }
    }

    final AtnState cStart = AtnStateUtil.getConstituentStartState(this);

    result.
      append("  ").
      append(cStart.toString()).
      append(" - ").
      append(this.toString());

    return result.toString();
  }


  public final AtnState getParentState() {
    AtnState result = null;

    if (parentStateNode != null) {
      result = parentStateNode.getData();
    }

    return result;
  }

  /**
   * Get the first state of this state's constituent.
   */
  public final AtnState getConstituentStartState() {
    AtnState result = this;

    for (; result != null; result = result.getParentState()) {
      if (result.isRuleStart()) break;
    }

    return result == null ? this : result;
  }

  private final Token computeNextToken(Token inputToken) {
    Token result = null;

    if (matchResult != null && !matchResult.inc()) {
      result = inputToken;
    }
    else {
      // get the next token without crossing a hard break boundary
      final Token nextToken = inputToken.getNextToken();
      if (nextToken != null) {
        result = rule.getGrammar().getAcceptedToken(rule.getTokenFilterId(), nextToken, false, inputToken, false, true, this);
      }
    }

    return result;
  }

  private Token computeRevisedToken() {
    Token result = null;

    if (!isSkipped()) {
      result = rule.getGrammar().getAcceptedToken(rule.getTokenFilterId(), inputToken.getRevisedToken(), true, inputToken, true, false, this);
    }

    return result;
  }

  /**
   * Determine whether this instance's token matches the step category
   * according to the grammar.
   */
  MatchResult tokenMatchesStepCategory(AtnGrammar grammar) {
    MatchResult result = null;
    boolean matched = false;

    final AtnRuleStep ruleStep = getRuleStep();
    if (ruleStep.getIgnoreToken()) {
      matched = ruleStep.verify(inputToken, this);
    }
    else {
      String category = ruleStep.getCategory();

      if (grammar.getCat2Classifiers().containsKey(category)) {
        for (AtnStateTokenClassifier classifier : grammar.getCat2Classifiers().get(category)) {
          final MatchResult matchResult = classifier.classify(inputToken, this);
          if (matchResult.matched() && applyTests()) {
            result = matchResult;
            break;
          }
        }
      }
      else {
        if (!grammar.getCat2Rules().containsKey(category)) {
          // use an "identity" classifier for literal grammar tokens.
          matched = category.equals(inputToken.getText());
        }

        // check for a feature that matches the category
        if (!matched) {
          matched = inputToken.getFeature(category, null) != null;
        }

        if (matched) {
          matched = ruleStep.verify(inputToken, this);
        }
      }
    }

    if (result == null) {
      result = MatchResult.getInstance(matched);
    }

    return result;
  }

  private final boolean applyTests() {
    return getRuleStep().verify(inputToken, this);
  }

  void markAsSkipped() {
    this._isSkipped = true;
    ++this.skipNum;
  }


  public String toString() {
    final StringBuilder result = new StringBuilder();

    result.append(rule.getRuleName());

    if (rule.getRuleId() != null) {
      result.append('[').append(rule.getRuleId()).append(']');
    }

    result.
      append('-').
      append(getRuleStep().getCategory()).
      append('(').
      append(inputToken).
      append(')');

    return result.toString();
  }


  private final boolean matchesRulePath(AtnState other) {
    if (other == null) return false;
    if (this == other) return true;

    boolean result = false;

    if (rule == other.getRule() &&
        stepNum == other.getStepNum() &&
        repeatNum == other.getRepeatNum()) {

      if (parentStateNode == other.getParentStateNode()) {
        result = true;
      }
      else if (parentStateNode != null && other.getParentStateNode() != null) {
        final AtnState parentState = parentStateNode.getData();
        final AtnState otherParentState = other.getParentStateNode().getData();

        if (parentState == otherParentState) {
          result = true;
        }
        else if (parentState != null) {
          result = parentState.matchesRulePath(otherParentState);
        }
      }
    }

    return result;
  }


  private final boolean applyAllPops(Tree<AtnState> nextStateNode, LinkedList<AtnState> states, LinkedList<AtnState> skipStates, Set<Integer> stopList) {
    boolean result = true;

    if (isRuleEnd()) {
      int statesSize = states.size();
      int skipStatesSize = skipStates.size();

      final AtnGrammar grammar = rule.getGrammar();
      Tree<AtnState> popStateNode = nextStateNode;
      AtnState popState = popState(popStateNode);
      while (popState != null) {
        boolean popVerified = true;

        // apply popState test
        result = popState.applyTests();
        
        if (!result) {
          popState.popFailed = true;
          if (trace) {
            System.out.println("POP tests FAILED\t" + popState.showStateContext() /*popState.showStateTree(true)*/);
            popState.applyTests();  // NOTE: this is here for debug stepping when tests fail unexpectedly
          }
        }
        else {
          popVerified = popState.verifyPop();

          if (!popVerified) {
            // note: result is still true because match succeeded; only the pop failed
            popState.popFailed = true;
            popStateNode.addChild(popState);

            if (trace) {
              System.out.println("POP verification FAILED\t" + popState.showStateContext() /*popState.showStateTree(true)*/);
            }

            if (popState.getPopCount() != 1) {
              // first pop has popCount 1 and is same as matching token, which will have
              // states added due to the match.
              popStateNode = popStateNode.addChild(popState);  //NOTE: this looks redundant, but isn't!

              // After the first pop, we need to consider forward states from each pop.
              addNextStates(grammar, states, skipStates, popState, popStateNode, true, true, stopList, true);
            }

            break;
          }
        }

        if (!result || !popVerified) {
          // back out of popping
          while (states.size() > statesSize) {
            if (trace) System.out.println("Failed pop backup ... removing queued state:\n" + states.getLast().showStateContext() /*states.getLast().showStatePath()*/);
            states.removeLast();
          }
          while (skipStates.size() > skipStatesSize) skipStates.removeLast();

          break;
        }

        popStateNode = popStateNode.addChild(popState);

        if (trace) {
          System.out.println("POP \t" + popState.showStateContext() /*popState.showStateTree(true)*/);
        }

        statesSize = states.size();
        skipStatesSize = skipStates.size();

        if (popState.getPopCount() != 1) {
          // first pop has popCount 1 and is same as matching token, which will have
          // states added due to the match.

          // After the first pop, we need to consider forward states from each pop.
          if (addNextStates(grammar, states, skipStates, popState, popStateNode, true, true, stopList, true)) {
            if (!popState.isRuleEnd(false)) {
              // we need to stop popping when we get to one that isn't a rule end
              popState = null;
              
              if (trace) {
                System.out.println("\t *** POP not ruleEnd!");
              }
            }
          }
        }

        if (popState != null) {
          popState = popState.popState(popStateNode);
        }
        else break;
      }

      if (result) {
        // remove now unnecessary skipped states
        for (AtnState parentState = this; skipStates.size() > 0 && parentState != null; parentState = parentState.getParentState()) {
          if (parentState == this || parentState.getMatched()) {
            for (Iterator<AtnState> skipIter = skipStates.iterator(); skipIter.hasNext(); ) {
              final AtnState skipState = skipIter.next();
              if (parentState.encompassesToken(skipState.getInputToken())) {
                skipIter.remove();
              }
            }
          }
        }
      }
    }
    else {
      if (trace) {
        System.out.println("POP FAIL (NotRuleEnd)\t" + nextStateNode.getData().showStateContext() /*nextStateNode.getData().showStateTree(true)*/);
      }
    }

    return result;
  }

  /**
   * Determine whether this state's input token encompasses the given token.
   */
  private final boolean encompassesToken(Token token) {
    return inputToken.encompasses(token);
  }

  private final boolean clusterConditionFails(LinkedList<AtnState> states) {
    // check for contradiction earlier in the state tree
    boolean result = clusterConditionFailsBackward();

    if (result && trace) {
      System.out.println("***Cluster condition fails (backward) for state: ***\n" + showStateContext() /*showStateTree(true)*/);
    }

    final boolean hasClusterFlag = getRuleStep().getClusterFlag();

    if (!result) {
      if (!result) {
        // check for contradition later in the state tree
        result = clusterConditionFailsForward(hasClusterFlag);

        if (result && trace) {
          System.out.println("***Cluster condition fails (forward) for state: ***\n" + showStateContext() /*showStateTree(true)*/);
        }
      }
      if (!result && hasClusterFlag) {
        // check this 'cluster' state against those on the queue
        result = removeInvalidQueuedStates(states);

        if (result && trace) {
          System.out.println("***Cluster condition fails (queued) for state: ***\n" + showStateContext() /*showStateTree(true)*/);
        }
      }
    }

    if (!result && !hasClusterFlag) {
      // check reverse case where a 'cluster'ed item on the queue relates to this new (non-cluster) state
      result = checkQueuedClusterConditions(states);
    }

    return result;
  }

  /**
   * Check for cluster (greedy) flag in this state's path (parents)
   * being broken by the potential addition of this state.
   */
  private final boolean clusterConditionFailsBackward() {
    boolean result = false;

    final String curCat = getRuleStep().getCategory();
    final int tokenStart = inputToken.getStartIndex();

    // look back for immediate ruleStep category (token match or constituent pop) repeat.
    for (AtnState priorMatch = getParentState() ;
         priorMatch != null ;
         priorMatch = priorMatch.getParentState()) {

      if (priorMatch.getMatched() || priorMatch.isPoppedState()) {
        // if the next token doesn't start at my token's start, we've gone too far
        final Token nextToken = priorMatch.getInputToken().getNextToken();
        if (nextToken != null) {
          final int nextTokenStart = nextToken.getStartIndex();
          if (nextTokenStart < tokenStart) break;
        }

        final AtnRuleStep priorMatchStep = priorMatch.getRuleStep();

        if (curCat.equals(priorMatchStep.getCategory())) {
          if (priorMatchStep.getClusterFlag()) {  // cluster flag is set
            // found matching category state
            if (pushState != priorMatch.pushState) {
              // different constituent ==> cluster (greedy) condition fails
              result = true;
            }
          }
          break;
        }
      }
    }

    if (!result) {
      // check push states
      for (AtnState pushState = this.getPushState(); pushState != null; pushState = pushState.getPushState()) {
        final AtnRuleStep pushStep = pushState.getRuleStep();
        if (curCat.equals(pushStep.getCategory())) {
          if (pushStep.getClusterFlag()) {  // cluster flag is set
            result = true;
          }
          break;
        }
      }
    }

    return result;
  }

  /**
   * Check for cluster (greedy) flag already broken in the "future", fixing
   * if found (and returning true=failed since tree will be fixed and the
   * proposed state should not be added.
   */
  private final boolean clusterConditionFailsForward(boolean hasClusterFlag) {
    boolean result = false;

    //if (!getRuleStep().getClusterFlag()) return result;

    final String curCat = getRuleStep().getCategory();
    final int tokenStart = inputToken.getStartIndex();

    // look forward for ruleStep category (token match or constituent push)
    // by scanning nextSiblings, parent, nextSiblings, parent, nextSiblings, etc.
    AtnState refState = this;
    Tree<AtnState> forwardStateNode = refState.getParentStateNode().getNextSibling();
    while (forwardStateNode != null || refState != null) {
      while (forwardStateNode == null) {
        refState = refState.getParentState();
        if (refState == null) break;
        forwardStateNode = refState.getParentStateNode().getNextSibling();
      }
      if (forwardStateNode == null) break;

      // search forwardStateNode's tree for match
      final Tree<AtnState> forwardMatchNode = findNode(forwardStateNode, curCat, tokenStart, hasClusterFlag);
      if (forwardMatchNode != null) {
        if (hasClusterFlag) {
          if (trace) System.out.println("Forward cluster failure ... moving state:\n" +
                                        forwardMatchNode.getData().showStateContext() /*forwardMatchNode.getData().showStatePath()*/ +
                                        "\n\tto\n" +
                                        parentStateNode.getData().showStateContext() /*parentStateNode.getData().showStatePath()*/);

          // prune/graft match into this state's place
          forwardMatchNode.prune(true, true);
          parentStateNode.addChild(forwardMatchNode);
        }

        result = true;
        break;
      }

      // keep searching forward
      forwardStateNode = forwardStateNode.getNextSibling();
    }


    return result;
  }

  private final Tree<AtnState> findNode(Tree<AtnState> stateNode, String category, int tokenStart, boolean requireMatch) {
    Tree<AtnState> result = null;

    for (Iterator<Tree<AtnState>> iter = stateNode.iterator(Tree.Traversal.DEPTH_FIRST) ; iter.hasNext() ;) {
      final Tree<AtnState> curNode = iter.next();
      final AtnState curState = curNode.getData();
      if (curState != null) {
        // make sure we haven't gone too far
        final int curTokenStart = curState.getInputToken().getStartIndex();
        if (curTokenStart != tokenStart) break;

        if (!requireMatch || curState.getMatched() || curState.getPushState() == curState.getParentState()) {
          final AtnRuleStep curRuleStep = curState.getRuleStep();
          final String curCat = curRuleStep.getCategory();
          if (category.equals(curCat)) {
            if (requireMatch || curRuleStep.getClusterFlag()) {
              // found match!
              result = curNode;
              break;
            }
          }
        }
      }
    }

    return result;
  }

  private final boolean removeInvalidQueuedStates(LinkedList<AtnState> states) {
    boolean result = false;

    final String curCat = getRuleStep().getCategory();
    final int tokenStart = inputToken.getStartIndex();

    for (Iterator<AtnState> stateIter = states.iterator(); stateIter.hasNext(); ) {
      final AtnState state = stateIter.next();
      final int curTokenStart = state.getInputToken().getStartIndex();
      if (curTokenStart == tokenStart) {
        final String category = state.getRuleStep().getCategory();
        // NOTE: only conditioned on category match since clustering failure is detected across constituents.
        if (category.equals(curCat)) {

          // determine which comes sooner: this or state
          final boolean thisPrecedes = this.statePrecedes(state);

          if (thisPrecedes) {
            // if this comes sooner, remove state and don't fail to add this
            if (trace) System.out.println("Failed cluster condition ... removing queued state:\n" + state.showStateContext() /*state.showStatePath()*/);
            stateIter.remove();
          }
          else {
            // else, fail this
            result = true;
          }
        }
      }
    }

    return result;
  }

  private final boolean checkQueuedClusterConditions(LinkedList<AtnState> states) {
    boolean result = false;

    final String curCat = getRuleStep().getCategory();
    final int tokenStart = inputToken.getStartIndex();

    for (Iterator<AtnState> stateIter = states.iterator(); stateIter.hasNext(); ) {
      final AtnState state = stateIter.next();
      if (!state.getRuleStep().getClusterFlag()) continue;

      final int curTokenStart = state.getInputToken().getStartIndex();
      if (curTokenStart == tokenStart) {
        boolean foundCC = false;  // cluster contradition

        final String category = state.getRuleStep().getCategory();

        // NOTE: only conditioned on category match since clustering failure is detected across constituents.
        if (category.equals(curCat)) {
          foundCC = true;
        }
        else {
          // check queued state's push states
          for (AtnState pushState = state.getPushState(); pushState != null; pushState = pushState.getPushState()) {
            final AtnRuleStep pushStep = pushState.getRuleStep();
            if (curCat.equals(pushStep.getCategory())) {
              foundCC = pushStep.getClusterFlag();
              break;
            }
          }
        }

        if (foundCC) {
          // determine which comes sooner: this or state
          final boolean thisPrecedes = this.statePrecedes(state);

          if (thisPrecedes) {
            // if this doesn't come sooner, remove state and don't fail to add this
            if (trace) System.out.println("Failed cluster condition ... removing queued state:\n" + state.showStateContext() /*state.showStatePath()*/);
            stateIter.remove();
          }
          else {
            // else, fail this
            result = true;
            break;
          }
        }
      }
    }

    return result;
  }

  private final boolean statePrecedes(AtnState otherState) {
    boolean result = false;

    // need to find common push and use ruleStepNum to decide
    final AtnState commonPushState = getCommonPushState(otherState);
    final int myRuleStepNum = this.getRuleStepNum(commonPushState);
    final int otherRuleStepNum = otherState.getRuleStepNum(commonPushState);

    // this precedes other if its ruleStepNum < other's ruleStepNum
    result = myRuleStepNum < otherRuleStepNum;

    return result;
  }

  private final List<AtnState> getPushPath() {
    final LinkedList<AtnState> result = new LinkedList<AtnState>();

    AtnState pushState = this.getPushState();
    while (pushState != null) {
      result.addFirst(pushState);
      pushState = pushState.getPushState();
    }

    return result;
  }

  private final AtnState getCommonPushState(AtnState otherState) {
    AtnState result = null;

    final Iterator<AtnState> myPushPathIter = this.getPushPath().iterator();
    final Iterator<AtnState> otherPushPathIter = otherState.getPushPath().iterator();

    while (myPushPathIter.hasNext() && otherPushPathIter.hasNext()) {
      final AtnState myPushState = myPushPathIter.next();
      final AtnState otherPushState = otherPushPathIter.next();

      if (myPushState != otherPushState) {
        break;  // diverged
      }
      else {
        result = myPushState;
      }
    }

    return result;
  }

  private final int getRuleStepNum(AtnState pushState) {
    int result = -1;

    for (AtnState curState = this; curState != null; curState = curState.getPushState()) {
      if (curState.getPushState() == pushState) {
        result = curState.getStepNum();
        break;
      }
    }

    return result;
  }


  private static boolean trace = false;
  private static boolean traceflow = false;
  private static boolean stepThruFlag = false;
  private static String stepThruRuleId = null;
  private static String stepThruRuleStep = null;
  private static String stepThruText = null;

  public static final void setTrace(boolean traceValue) {
    trace = traceValue;
  }

  public static final void setTraceFlow(boolean traceflowValue) {
    traceflow = traceflowValue;
  }

  public static final void setStepThruDebugging(String ruleId, String ruleStep, String text) {
    stepThruFlag = (ruleId != null && ruleStep != null && text != null);
    stepThruRuleId = ruleId;
    stepThruRuleStep = ruleStep;
    stepThruText = text;
  }

  static List<CategorizedToken> computeTokens(Tree<AtnState> stateNode) {
    final List<CategorizedToken> result = new ArrayList<CategorizedToken>();
    final LinkedList<Tree<AtnState>> stateNodes = stateNode.getRootPath();

    for (int pathIndex = 1; pathIndex < stateNodes.size(); ++pathIndex) {
      final Tree<AtnState> pathStateNode = stateNodes.get(pathIndex);
      final AtnState pathState = pathStateNode.getData();
      if (pathState.getMatched() && pathState.getRuleStep().consumeToken()) {
        result.add(new CategorizedToken(pathState.getInputToken(), pathState.getRuleStep().getCategory()));
      }
    }

    return result;
  }

  static boolean matchTokenToRule(AtnGrammar grammar, LinkedList<AtnState> states, LinkedList<AtnState> skipStates, Set<Integer> stopList, AtomicBoolean die) {
    boolean result = false;

    while ((states.size() + skipStates.size() > 0) && !result && (die == null || !die.get())) {
      final AtnState curstate = states.size() > 0 ? states.removeFirst() : skipStates.removeFirst();

      if (stepThruFlag) {
        if ((stepThruRuleId != null && stepThruRuleId.equals(curstate.getRule().getRuleId())) &&
            (stepThruRuleStep != null && stepThruRuleStep.equals(curstate.getRuleStep().getCategory())) &&
            (stepThruText != null && stepThruText.equals(curstate.getInputToken().getText()))) {
          // set a breakpoint on the following line when using stepThru debugging
          final boolean stopHere = true;
        }
      }

      boolean success = false;
      final boolean meetsRequirements = curstate.meetsRequirements();
      MatchResult matchResult = null;
      boolean matches = meetsRequirements;
      if (matches) {

        if (traceflow) {
          System.out.println("traceflow--AtnState matching " + curstate.toString());
        }

        matchResult = curstate.tokenMatchesStepCategory(grammar);
        matches = matchResult.matched();
      }
      final Tree<AtnState> nextStateNode = curstate.parentStateNode.addChild(curstate);

      if (trace) {
        System.out.println("match=" + matches + "\t" + curstate.showStateContext() /*curstate.showStateTree(matches)*/);
      }

      if (matches) {

        if (traceflow) {
          System.out.println("traceflow--AtnState MATCH " + curstate.toString());
        }

        matches = curstate.applyAllPops(nextStateNode, states, skipStates, stopList);

        if (traceflow && !matches) {
          System.out.println("traceflow--AtnState DROPPED match " + curstate.toString());
        }
      }

      if (matches) {
        success = true;
        curstate.setMatchResult(matchResult);

        if (curstate.isValidEnd(stopList)) {
          // found a valid full parse
          result = true;
        }
      }
      else {
        matchResult = null;
      }

      success = addNextStates(grammar, states, skipStates, curstate, nextStateNode,
                              false, matches, stopList, meetsRequirements);
    }

    return result;
  }

  private static boolean addNextStates(AtnGrammar grammar, LinkedList<AtnState> states, LinkedList<AtnState> skipStates, AtnState curstate, Tree<AtnState> nextStateNode, boolean isPop, boolean inc, Set<Integer> stopList, boolean meetsRequirements) {
    if (curstate == null) return false;

    boolean foundOne = inc || isPop;
    AtnState nextstate = null;

    // increment token
    if (foundOne) {
      nextstate = curstate.getNextRepeatState(nextStateNode, isPop ? curstate : null, inc, stopList);

      if (nextstate != null) {
        addState(grammar, states, skipStates, nextstate, stopList);
      }

      nextstate = curstate.getNextStepState(nextStateNode, inc, stopList);
      if (nextstate != null) {
        addState(grammar, states, skipStates, nextstate, stopList);
      }

      // revise token
      nextstate = curstate.getNextRevisedState();
      if (nextstate != null) {
        addState(grammar, states, skipStates, nextstate, stopList);
        foundOne = true;
      }

      return foundOne;
    }

    // revise token
    nextstate = curstate.getNextRevisedState();
    if (nextstate != null) {
      addState(grammar, states, skipStates, nextstate, stopList);
      foundOne = true;
    }

    // account for optional step.
    if (curstate.getRuleStep().isOptional() && !curstate.isRepeat()) {
      nextstate = curstate.getSkipOptionalState();
      if (nextstate != null) {
        addState(grammar, states, skipStates, nextstate, stopList);
        foundOne = true;
      }
    }

    // apply (push) rules
    if (meetsRequirements) {
      final String category = curstate.getRuleStep().getCategory();
      if (grammar.getCat2Rules().containsKey(category)) {
        foundOne = true;

        for (AtnRule rule : grammar.getCat2Rules().get(category)) {
          AtnState pushState = new AtnState(curstate.getInputToken(), rule, 0, nextStateNode, curstate.parseOptions, 0, 0, curstate);

          // add push state and states skipping initial optional steps after push
          for (; pushState != null; pushState = pushState.getSkipOptionalState()) {
            addState(grammar, states, skipStates, pushState, stopList);
          }
        }

        // skip constituents
        if (curstate.canBeSkipped()) {
          final AtnState dupstate = new AtnState(curstate);
          final Tree<AtnState> dupstateNode = new Tree<AtnState>(dupstate);
          nextstate = dupstate.getNextSkippedState(dupstateNode, stopList);
          if (nextstate != null) {
            nextStateNode.getParent().addChild(dupstateNode);
            dupstate.parentStateNode = nextStateNode.getParent();
            addState(skipStates, nextstate);
          }
        }
      }
    }

    // skip tokens
    if (!foundOne) {
      nextstate = curstate.getNextSkippedState(nextStateNode, stopList);
      if (nextstate != null) addState(skipStates, nextstate);
    }

    return foundOne;
  }

  private static final void addState(AtnGrammar grammar, LinkedList<AtnState> states,
                                     LinkedList<AtnState> skipStates, AtnState nextstate,
                                     Set<Integer> stopList) {

    // if can't add the state due to cluster condition failure, try revision, optional bypass, and skipping
    if (!addState(states, nextstate)) {
      final Tree<AtnState> nextStateNode = nextstate.parentStateNode.addChild(nextstate);
      addNextStates(grammar, states, skipStates, nextstate, nextStateNode, false, false, stopList, false/*, null*/);
    }
  }

  private static final boolean addState(LinkedList<AtnState> states, AtnState nextstate) {
    boolean result = true;

    boolean isDup = false;

    // check for duplicates
    final Token token = nextstate.getInputToken();
    for (AtnState state : states) {
      if (token == state.getInputToken() && nextstate.matchesRulePath(state)) {
        isDup = true;
        break;
      }
    }

    // check cluster (greedy) flag
    if (!isDup && nextstate.clusterConditionFails(states)) {
      // greedy conditions fail
      isDup = true;
      result = false;
    }

    if (!isDup) {
      if (trace) System.out.println("\nQueuing State: " + nextstate.showStateContext() /*nextstate.showStatePath()*/);
      states.addLast(nextstate);
    }
    else {
      if (trace) System.out.println("\nDiscarding State (clusterFail=" + !result + "): " + nextstate.showStateContext() /*nextstate.showStatePath()*/);
    }

    return result;
  }
}
