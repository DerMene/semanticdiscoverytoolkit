/*
    Copyright 2013 Semantic Discovery, Inc. (www.semanticdiscovery.com)

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
//import SemanticDiscovery.Util.LRU;
using System;
using System.Collections.Generic;

namespace SemanticDiscovery.Util.Attribute
{
  /// <summary>
  /// A base/reference implementation of the attribute classifier interface.
  /// <p>
  /// </summary>
  /// <author>Spence Koehler</author>
  public abstract class BaseAttributeClassifier<E> 
    : AttributeClassifier<E>
  {
    /// @return E.valueOf(upperCaseType), allowing IllegalArgumentException to be thrown.
    protected abstract E GetValueOf(String upperCaseType);

    /// classify the non-empty type that did *not* match an enum constant.
    protected abstract Attribute<E> ClassifyOtherAttribute(String type);


    public const int DEFAULT_CACHE_SIZE = 10000;

    //private LRU<String, Attribute<E>> cache;
    private Dictionary<string, Attribute<E>> cache;

    protected BaseAttributeClassifier() : this(DEFAULT_CACHE_SIZE) {}

    protected BaseAttributeClassifier(int cacheSize) {
      //this.cache = new LRU<String, Attribute<E>>(cacheSize);
      this.cache = new Dictionary<string,Attribute<E>>();
    }

    public Attribute<E> GetAttribute(String type) {
      Attribute<E> result = null;

      if (!string.IsNullOrEmpty(type)) 
      {
        // try the cache
        result = cache.Get(type);

        if (result == null) {
          // try one-to-one attribute lookup
          try {
            E att = GetValueOf(type.ToUpper());
            result = new Attribute<E>(att);
          }
          catch (ArgumentException e) {
            // not an enum constant; try a more aggressive technique
            result = ClassifyOtherAttribute(type);
          }

          cache.Put(type, result);
        }
      }

      return result;
    }

    /// Convience helper method for adding a (possibly ambiguous) attribute.
    /// 
    /// Intended usage: call "result = addAtt(att, result)" for each (ambiguous) att.
    protected Attribute<E> AddAtt(E att, Attribute<E> result) 
    {
      if (result == null) {
        result = new Attribute<E>(att);
      }
      else {
        result.AddAmbiguity(new Attribute<E>(att));
      }

      return result;
    }
  }
}
